// SPDX-License-Identifier: LGPL-3.0-or-later
// Nocturna's independent OpenGL/D3D12 bridge. No Super Resolution source is used.
#include <windows.h>
#include <d3d12.h>
#include <d3d12sdklayers.h>
#include <dxgi1_4.h>
#include <wrl/client.h>
#include <GL/gl.h>
#include <jni.h>
#include <ffx_api.h>
#include <dx12/ffx_api_dx12.h>
#include <ffx_upscale.h>
#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <regex>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdio>
#include <limits>
#include <mutex>
#include <unordered_map>
#include <cmath>
#include "fence_wait.h"

using Microsoft::WRL::ComPtr;
namespace {
void check(HRESULT code, const char* operation) {
    nocturna::requireSuccess(code, operation);
}
void ffxCheck(ffxReturnCode_t code, const char* operation) {
    if (code != FFX_API_RETURN_OK) throw std::runtime_error(std::string(operation) + " FFX=" + std::to_string(code));
}
template<class T> T proc(const char* name) {
    auto address = wglGetProcAddress(name);
    if (!address || address == reinterpret_cast<PROC>(1) || address == reinterpret_cast<PROC>(2)
        || address == reinterpret_cast<PROC>(3) || address == reinterpret_cast<PROC>(-1))
        throw std::runtime_error(std::string("Missing GL entry point: ") + name);
    return reinterpret_cast<T>(address);
}
struct Gl {
    decltype(&glGetError) error = &glGetError;
    void (APIENTRY *getBytes)(GLenum, GLubyte*) = proc<decltype(getBytes)>("glGetUnsignedBytevEXT");
    void (APIENTRY *createMemory)(GLsizei, GLuint*) = proc<decltype(createMemory)>("glCreateMemoryObjectsEXT");
    void (APIENTRY *deleteMemory)(GLsizei, const GLuint*) = proc<decltype(deleteMemory)>("glDeleteMemoryObjectsEXT");
    void (APIENTRY *memoryParameter)(GLuint, GLenum, const GLint*) = proc<decltype(memoryParameter)>("glMemoryObjectParameterivEXT");
    void (APIENTRY *importMemory)(GLuint, std::uint64_t, GLenum, void*) = proc<decltype(importMemory)>("glImportMemoryWin32HandleEXT");
    void (APIENTRY *createTextures)(GLenum, GLsizei, GLuint*) = proc<decltype(createTextures)>("glCreateTextures");
    void (APIENTRY *storage)(GLuint, GLsizei, GLenum, GLsizei, GLsizei, GLuint, std::uint64_t) = proc<decltype(storage)>("glTextureStorageMem2DEXT");
    void (APIENTRY *textureParameter)(GLuint, GLenum, GLint) = proc<decltype(textureParameter)>("glTextureParameteri");
    void (APIENTRY *genSemaphores)(GLsizei, GLuint*) = proc<decltype(genSemaphores)>("glGenSemaphoresEXT");
    void (APIENTRY *deleteSemaphores)(GLsizei, const GLuint*) = proc<decltype(deleteSemaphores)>("glDeleteSemaphoresEXT");
    void (APIENTRY *importSemaphore)(GLuint, GLenum, void*) = proc<decltype(importSemaphore)>("glImportSemaphoreWin32HandleEXT");
    void (APIENTRY *semaphoreValue)(GLuint, GLenum, const std::uint64_t*) = proc<decltype(semaphoreValue)>("glSemaphoreParameterui64vEXT");
    void (APIENTRY *signal)(GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*) = proc<decltype(signal)>("glSignalSemaphoreEXT");
    void (APIENTRY *wait)(GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*) = proc<decltype(wait)>("glWaitSemaphoreEXT");
    void verify(const char* operation) const {
        GLenum code = error();
        if (code != GL_NO_ERROR) throw std::runtime_error(std::string(operation) + " GL=" + std::to_string(code));
    }
};
struct Handle {
    HANDLE value = nullptr;
    ~Handle() { if (value) CloseHandle(value); }
};
struct Slot {
    std::array<Handle, 4> handles;
    std::array<ComPtr<ID3D12Resource>, 4> resources;
    std::array<GLuint, 4> textures{};
    std::array<GLuint, 4> memories{};
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> list;
    std::uint64_t completion = 0;
};
struct Context {
    Gl gl;
    HGLRC owner = wglGetCurrentContext();
    HMODULE library = nullptr;
    PfnFfxCreateContext create = nullptr;
    PfnFfxDestroyContext destroy = nullptr;
    PfnFfxQuery query = nullptr;
    PfnFfxDispatch dispatch = nullptr;
    ffxContext effect = nullptr;
    // FFX requires the entire creation descriptor chain to outlive the context.
    ffxOverrideVersion selectedVersion{};
    ffxCreateContextDescUpscaleVersion apiVersion{};
    ffxCreateBackendDX12Desc backend{};
    ffxCreateContextDescUpscale description{};
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<ID3D12Fence> fence;
    Handle event;
    Handle fenceHandle;
    GLuint semaphore = 0;
    std::array<Slot, 3> slots;
    unsigned slot = 0;
    std::uint64_t serial = 0;
    int width, height, outputWidth, outputHeight;
    std::string provider;

    Context(int w, int h, int ow, int oh) : width(w), height(h), outputWidth(ow), outputHeight(oh) {
        if (!owner || w <= 0 || h <= 0 || ow < w || oh < h || ow > 16384 || oh > 16384)
            throw std::runtime_error("Invalid FSR context dimensions or no current GL context");
    }
    void requireOwner() const {
        if (wglGetCurrentContext() != owner) throw std::runtime_error("FSR used outside its owning GL context");
    }
    void waitCpu(std::uint64_t value) {
        nocturna::waitForFence(*fence.Get(), *device.Get(), event.value, value);
    }
    void texture(Slot& s, unsigned i, int w, int h, DXGI_FORMAT format, GLenum glFormat) {
        D3D12_HEAP_PROPERTIES heap{};
        heap.Type = D3D12_HEAP_TYPE_DEFAULT;
        heap.CreationNodeMask = heap.VisibleNodeMask = 1;
        D3D12_RESOURCE_DESC desc{};
        desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
        desc.Width = w; desc.Height = h; desc.DepthOrArraySize = 1; desc.MipLevels = 1;
        desc.Format = format; desc.SampleDesc.Count = 1;
        desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET | D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS
                   | D3D12_RESOURCE_FLAG_ALLOW_SIMULTANEOUS_ACCESS;
        check(device->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_SHARED, &desc,
            D3D12_RESOURCE_STATE_COMMON, nullptr, IID_PPV_ARGS(&s.resources[i])), "Create shared FSR texture");
        auto& handle = s.handles[i];
        check(device->CreateSharedHandle(s.resources[i].Get(), nullptr, GENERIC_ALL, nullptr, &handle.value), "Share texture");
        gl.createMemory(1, &s.memories[i]);
        GLint dedicated = GL_TRUE;
        gl.memoryParameter(s.memories[i], 0x9581 /* DEDICATED_MEMORY_OBJECT_EXT */, &dedicated);
        gl.importMemory(s.memories[i], 0, 0x958A /* D3D12_RESOURCE */, handle.value);
        gl.createTextures(GL_TEXTURE_2D, 1, &s.textures[i]);
        gl.storage(s.textures[i], 1, glFormat, w, h, s.memories[i], 0);
        gl.textureParameter(s.textures[i], GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        gl.textureParameter(s.textures[i], GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        gl.textureParameter(s.textures[i], GL_TEXTURE_WRAP_S, 0x812F /* CLAMP_TO_EDGE */);
        gl.textureParameter(s.textures[i], GL_TEXTURE_WRAP_T, 0x812F);
        gl.verify("Import shared FSR texture");
    }
    void initialize(const wchar_t* path, int family) {
        requireOwner();
        library = LoadLibraryExW(path, nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32);
        if (!library) throw std::runtime_error("Cannot load AMD's signed FSR upscaler DLL");
        create = reinterpret_cast<PfnFfxCreateContext>(GetProcAddress(library, "ffxCreateContext"));
        destroy = reinterpret_cast<PfnFfxDestroyContext>(GetProcAddress(library, "ffxDestroyContext"));
        query = reinterpret_cast<PfnFfxQuery>(GetProcAddress(library, "ffxQuery"));
        dispatch = reinterpret_cast<PfnFfxDispatch>(GetProcAddress(library, "ffxDispatch"));
        if (!create || !destroy || !query || !dispatch) throw std::runtime_error("Incomplete FSR API exports");
        LUID luid{};
        gl.getBytes(0x9599 /* DEVICE_LUID_EXT */, reinterpret_cast<GLubyte*>(&luid));
        gl.verify("Query GL adapter LUID");
        ComPtr<IDXGIFactory4> factory;
        check(CreateDXGIFactory1(IID_PPV_ARGS(&factory)), "Create DXGI factory");
        ComPtr<IDXGIAdapter1> adapter;
        check(factory->EnumAdapterByLuid(luid, IID_PPV_ARGS(&adapter)), "Match OpenGL adapter");
        if (GetEnvironmentVariableW(L"NOCTURNA_FSR_DEBUG", nullptr, 0)) {
            ComPtr<ID3D12Debug> debug;
            if (SUCCEEDED(D3D12GetDebugInterface(IID_PPV_ARGS(&debug)))) debug->EnableDebugLayer();
        }
        check(D3D12CreateDevice(adapter.Get(), D3D_FEATURE_LEVEL_12_0, IID_PPV_ARGS(&device)), "Create FSR D3D12 device");
        if (device->GetNodeCount() != 1) throw std::runtime_error("Linked-node adapters are not supported by this FSR bridge");

        std::uint64_t count = 0;
        ffxQueryDescGetVersions versions{{FFX_API_QUERY_DESC_TYPE_GET_VERSIONS, nullptr},
            FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE, device.Get(), &count, nullptr, nullptr};
        ffxCheck(query(nullptr, &versions.header), "Enumerate FSR providers");
        if (count == 0 || count > 128) throw std::runtime_error("No compatible FSR providers");
        std::vector<std::uint64_t> ids(count);
        std::vector<const char*> names(count);
        versions.versionIds = ids.data(); versions.versionNames = names.data();
        ffxCheck(query(nullptr, &versions.header), "Read FSR providers");
        std::uint64_t selected = 0;
        int selectedPatch = -1;
        const std::regex number(R"((\d+)\.(\d+)\.(\d+))");
        for (size_t i = 0; i < count; ++i) {
            std::string name = names[i] ? names[i] : "";
            std::smatch match;
            if (!std::regex_search(name, match, number)) continue;
            int major = std::stoi(match[1]), minor = std::stoi(match[2]), patch = std::stoi(match[3]);
            if (major == family && (major != 4 || minor == 1) && patch > selectedPatch) {
                selected = ids[i]; provider = name; selectedPatch = patch;
            }
        }
        if (!selected) throw std::runtime_error("Requested FSR " + std::to_string(family) + " provider unavailable on this GPU/driver");
        selectedVersion = {{FFX_API_DESC_TYPE_OVERRIDE_VERSION, nullptr}, selected};
        apiVersion = {{FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE_VERSION, &selectedVersion.header}, FFX_UPSCALER_VERSION};
        backend = {{FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12, &apiVersion.header}, device.Get()};
        auto& desc = description;
        desc.header = {FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE, &backend.header};
        desc.maxRenderSize = {static_cast<unsigned>(width), static_cast<unsigned>(height)};
        desc.maxUpscaleSize = {static_cast<unsigned>(outputWidth), static_cast<unsigned>(outputHeight)};
        desc.flags = FFX_UPSCALE_ENABLE_AUTO_EXPOSURE | FFX_UPSCALE_ENABLE_HIGH_DYNAMIC_RANGE;
        if (GetEnvironmentVariableW(L"NOCTURNA_FSR_DEBUG", nullptr, 0)) {
            desc.flags |= FFX_UPSCALE_ENABLE_DEBUG_CHECKING;
            desc.fpMessage = [](uint32_t, const wchar_t* message) { fwprintf(stderr, L"FFX: %ls\n", message); };
        }
        ffxCheck(create(&effect, &desc.header, nullptr), "Create requested FSR provider");
        ffxQueryGetProviderVersion actual{{FFX_API_QUERY_DESC_TYPE_GET_PROVIDER_VERSION, nullptr}, 0, nullptr};
        ffxCheck(query(&effect, &actual.header), "Verify selected FSR provider");
        if (actual.versionId != selected) throw std::runtime_error("AMD runtime changed the explicitly selected FSR provider");
        D3D12_COMMAND_QUEUE_DESC q{};
        q.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
        check(device->CreateCommandQueue(&q, IID_PPV_ARGS(&queue)), "Create FSR queue");
        check(device->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&fence)), "Create shared FSR fence");
        event.value = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (!event.value) throw std::runtime_error("Create FSR fence event failed");
        auto& handle = fenceHandle;
        check(device->CreateSharedHandle(fence.Get(), nullptr, GENERIC_ALL, nullptr, &handle.value), "Share FSR fence");
        gl.genSemaphores(1, &semaphore);
        gl.importSemaphore(semaphore, 0x9594 /* D3D12_FENCE_EXT */, handle.value);
        gl.verify("Import FSR semaphore");
        for (auto& s : slots) {
            texture(s, 0, width, height, DXGI_FORMAT_R16G16B16A16_FLOAT, 0x881A /* RGBA16F */);
            texture(s, 1, width, height, DXGI_FORMAT_R32_FLOAT, 0x822E /* R32F */);
            texture(s, 2, width, height, DXGI_FORMAT_R16G16_FLOAT, 0x822F /* RG16F */);
            texture(s, 3, outputWidth, outputHeight, DXGI_FORMAT_R16G16B16A16_FLOAT, 0x881A);
            check(device->CreateCommandAllocator(q.Type, IID_PPV_ARGS(&s.allocator)), "Create FSR allocator");
            check(device->CreateCommandList(0, q.Type, s.allocator.Get(), nullptr, IID_PPV_ARGS(&s.list)), "Create FSR command list");
            check(s.list->Close(), "Close initial FSR command list");
        }
    }
    void acquire() {
        requireOwner();
        slot = static_cast<unsigned>(serial % slots.size());
        waitCpu(slots[slot].completion);
    }
    void execute(float jitterX, float jitterY, float delta, float nearPlane, float farPlane, float fov, float sharpness, bool reset) {
        requireOwner();
        if (!std::isfinite(jitterX) || !std::isfinite(jitterY) || !std::isfinite(delta)
            || !std::isfinite(nearPlane) || !std::isfinite(farPlane) || !std::isfinite(fov)
            || !std::isfinite(sharpness) || delta <= 0 || nearPlane <= 0 || farPlane <= nearPlane
            || fov <= 0 || fov >= 3.14159265358979323846f || sharpness < 0 || sharpness > 1)
            throw std::runtime_error("Invalid FSR dispatch parameters");
        auto& s = slots[slot];
        check(s.allocator->Reset(), "Reset FSR allocator");
        check(s.list->Reset(s.allocator.Get(), nullptr), "Reset FSR list");
        std::array<D3D12_RESOURCE_BARRIER, 4> barriers{};
        for (unsigned i = 0; i < 4; ++i) {
            auto& b = barriers[i];
            b.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            b.Transition.pResource = s.resources[i].Get();
            b.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            b.Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            b.Transition.StateAfter = i == 3 ? D3D12_RESOURCE_STATE_UNORDERED_ACCESS : D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
        }
        s.list->ResourceBarrier(4, barriers.data());
        ffxDispatchDescUpscale desc{};
        desc.header = {FFX_API_DISPATCH_DESC_TYPE_UPSCALE, nullptr};
        desc.commandList = s.list.Get();
        desc.color = ffxApiGetResourceDX12(s.resources[0].Get());
        desc.depth = ffxApiGetResourceDX12(s.resources[1].Get());
        desc.motionVectors = ffxApiGetResourceDX12(s.resources[2].Get());
        desc.output = ffxApiGetResourceDX12(s.resources[3].Get(), FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
        desc.jitterOffset = {jitterX, -jitterY}; // GL bottom-left -> D3D top-left.
        desc.motionVectorScale = {static_cast<float>(width), static_cast<float>(height)};
        desc.renderSize = {static_cast<unsigned>(width), static_cast<unsigned>(height)};
        desc.upscaleSize = {static_cast<unsigned>(outputWidth), static_cast<unsigned>(outputHeight)};
        desc.frameTimeDelta = delta;
        desc.preExposure = 1.0f;
        desc.cameraNear = nearPlane; desc.cameraFar = farPlane; desc.cameraFovAngleVertical = fov;
        desc.viewSpaceToMetersFactor = 1.0f;
        desc.enableSharpening = sharpness > 0; desc.sharpness = sharpness;
        desc.reset = reset;
        ffxCheck(dispatch(&effect, &desc.header), "Dispatch FSR");
        for (auto& b : barriers) std::swap(b.Transition.StateBefore, b.Transition.StateAfter);
        s.list->ResourceBarrier(4, barriers.data());
        check(s.list->Close(), "Close FSR list");
        std::uint64_t ready = serial * 2 + 1, done = ready + 1;
        std::array<GLenum, 4> layouts{0x958D, 0x958D, 0x958D, 0x958D}; // GENERAL_EXT == D3D12 COMMON.
        gl.semaphoreValue(semaphore, 0x9595, &ready);
        gl.signal(semaphore, 0, nullptr, 4, s.textures.data(), layouts.data());
        glFlush();
        gl.verify("Release FSR resources to D3D12");
        check(queue->Wait(fence.Get(), ready), "Wait for GL inputs");
        ID3D12CommandList* lists[]{s.list.Get()};
        queue->ExecuteCommandLists(1, lists);
        check(queue->Signal(fence.Get(), done), "Signal FSR completion");
        s.completion = done;
        gl.semaphoreValue(semaphore, 0x9595, &done);
        gl.wait(semaphore, 0, nullptr, 4, s.textures.data(), layouts.data());
        gl.verify("Acquire FSR resources from D3D12");
        ++serial;
    }
    ~Context() {
        // The owner closes us on the render thread, with its GL context current.
        if (owner && owner == wglGetCurrentContext()) glFinish();
        if (queue && fence && event.value) {
            // Fence values are shared with GL; use a new value after all submitted work.
            auto finalValue = serial * 2 + 2;
            if (SUCCEEDED(queue->Signal(fence.Get(), finalValue))) {
                try { waitCpu(finalValue); } catch (...) { /* device removal: release CPU objects */ }
            }
        }
        if (effect && destroy) destroy(&effect, nullptr);
        if (owner == wglGetCurrentContext()) {
            for (auto& s : slots) {
                glDeleteTextures(4, s.textures.data());
                gl.deleteMemory(4, s.memories.data());
            }
            if (semaphore) gl.deleteSemaphores(1, &semaphore);
        }
        if (library) FreeLibrary(library);
    }
};
// Opaque IDs reject stale/foreign Java handles before dereferencing native memory.
// Keep only the registry container alive at process exit: GPU cleanup must not run under DllMain's loader lock.
struct Registry {
    std::mutex mutex;
    std::unordered_map<jlong, std::unique_ptr<Context>> contexts;
    jlong nextId = 1;
};
Registry& registry() {
    static auto* state = new Registry();
    return *state;
}
Context& checkedContext(Registry& state, jlong id) {
    auto found = state.contexts.find(id);
    if (found == state.contexts.end()) throw std::runtime_error("Invalid or closed FSR context");
    found->second->requireOwner();
    return *found->second;
}
void fail(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
}
#define JNI_METHOD(name) Java_net_irisshaders_iris_pathways_scaling_temporal_FsrNative_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv* env, jclass, jstring path, jint w, jint h, jint ow, jint oh, jint family) {
    try {
        if (!path) throw std::runtime_error("FSR runtime path is null");
        const auto length = env->GetStringLength(path);
        std::wstring dll(static_cast<size_t>(length), L'\0');
        env->GetStringRegion(path, 0, length, reinterpret_cast<jchar*>(dll.data()));
        if (env->ExceptionCheck()) return 0;
        auto context = std::make_unique<Context>(w, h, ow, oh);
        context->initialize(dll.c_str(), family);
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        if (state.nextId == std::numeric_limits<jlong>::max()) throw std::runtime_error("FSR context IDs exhausted");
        auto id = state.nextId++;
        state.contexts.emplace(id, std::move(context));
        return id;
    } catch (const std::exception& error) { fail(env, error); return 0; }
}
extern "C" JNIEXPORT jintArray JNICALL JNI_METHOD(acquire)(JNIEnv* env, jclass, jlong pointer) {
    try {
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        auto& c = checkedContext(state, pointer);
        c.acquire();
        auto result = env->NewIntArray(4);
        if (result) env->SetIntArrayRegion(result, 0, 4, reinterpret_cast<const jint*>(c.slots[c.slot].textures.data()));
        return result;
    } catch (const std::exception& error) { fail(env, error); return nullptr; }
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(provider)(JNIEnv* env, jclass, jlong pointer) {
    try {
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        return env->NewStringUTF(checkedContext(state, pointer).provider.c_str());
    } catch (const std::exception& error) { fail(env, error); return nullptr; }
}
extern "C" JNIEXPORT jfloatArray JNICALL JNI_METHOD(jitter)(JNIEnv* env, jclass, jlong pointer, jlong frame) {
    try {
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        auto& c = checkedContext(state, pointer);
        int32_t phases = 0;
        ffxQueryDescUpscaleGetJitterPhaseCount count{{FFX_API_QUERY_DESC_TYPE_UPSCALE_GETJITTERPHASECOUNT, nullptr},
            static_cast<unsigned>(c.width), static_cast<unsigned>(c.outputWidth), &phases};
        ffxCheck(c.query(&c.effect, &count.header), "Query FSR jitter phase count");
        if (phases <= 0 || frame < 0) throw std::runtime_error("Invalid FSR jitter phase");
        float offsets[2]{};
        ffxQueryDescUpscaleGetJitterOffset offset{{FFX_API_QUERY_DESC_TYPE_UPSCALE_GETJITTEROFFSET, nullptr},
            static_cast<int32_t>(frame % phases), phases, &offsets[0], &offsets[1]};
        ffxCheck(c.query(&c.effect, &offset.header), "Query FSR jitter offset");
        offsets[1] = -offsets[1]; // API top-left -> projection offset in OpenGL bottom-left coordinates.
        auto result = env->NewFloatArray(2);
        if (result) env->SetFloatArrayRegion(result, 0, 2, offsets);
        return result;
    } catch (const std::exception& error) { fail(env, error); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(execute)(JNIEnv* env, jclass, jlong pointer, jfloat x, jfloat y, jfloat delta, jfloat nearPlane, jfloat farPlane, jfloat fov, jfloat sharpness, jboolean reset) {
    try {
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        checkedContext(state, pointer).execute(x, y, delta, nearPlane, farPlane, fov, sharpness, reset);
    }
    catch (const std::exception& error) { fail(env, error); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(close)(JNIEnv* env, jclass, jlong pointer) {
    if (!pointer) return;
    try {
        auto& state = registry();
        std::scoped_lock lock(state.mutex);
        checkedContext(state, pointer);
        state.contexts.erase(pointer);
    } catch (const std::exception& error) { fail(env, error); }
}
