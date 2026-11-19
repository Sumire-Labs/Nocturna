# Optional temporal FSR backend

This is an independent LGPL-3.0-or-later OpenGL/D3D12 JNI bridge, targeting Windows x64 and Java 17.
The shipped `nocturna_fsr.dll` is reproducible from these sources with MSVC/CMake:

```powershell
./scripts/Build-FsrBridge.ps1 -JavaHome 'C:/path/to/jdk-17' -UpdateManifest
```

The four unmodified `vendor/` headers are MIT-licensed AMD FSR SDK API headers, revision
`7539ae8b6568ad29ed78a753404351daf3b5cca7`. Each retains AMD's full license notice.
No Super Resolution GPL code, FSR ML kernels, AMD effect binary, Vulkan backend, or newer Sodium code is included.

AMD's signed runtime has separate binary distribution terms. It remains an optional, external dependency:

```powershell
./scripts/Install-FsrRuntime.ps1 -GameDirectory 'C:/path/to/minecraft-instance'
```

The installer pins the upstream revision and SHA-256 and checks AMD's Authenticode signature.
The renderer also checks the SHA-256 before loading it. No automatic network requests occur in the game.
FSR 2.3.4, 3.1.6 and 4.1.2 providers are queried by version and explicitly selected; an unsupported
requested version is never silently reported as another FSR version.

Shared texture and fence NT handles remain open for the entire GL import lifetime. The complete FFX
creation descriptor chain also remains alive until the FFX context is destroyed. Both details matter
to real drivers even when initial import/context creation reports success.

OpenGL's adapter LUID chooses the D3D12 adapter. Three command allocators and texture sets permit
in-flight work; D3D12 fences imported as GL semaphores order access without per-frame CPU readback.
Context creation performs one constant-color transport/readback check. A provider that accepts a
configuration but returns an invalid image is rejected before pack jitter is enabled. For example,
the tested 8060S driver/4.1.2 runtime returns black for a 64x64 input, despite accepting its context.
That configuration falls back to FSR 1, rather than presenting black. Ordinary resolutions are tested separately.

See `docs/temporal-fsr-2026-11-19.md` for the shader-pack contract, verification and limitations.

The native API uses opaque, non-reused IDs rather than exposing C++ pointers. Invalid, stale
and wrong-context operations are rejected before touching GPU resources (`close(0)` is a no-op); Java owners
also reject use after close. The small fence protocol unit test covers device removal
(`UINT64_MAX`), event setup failure, timeout and device loss after a wait without faulting the GPU.
`Build-FsrBridge.ps1` runs it, and the `native-fsr` Windows CI builds/runs it independently.
The registry container is process-lived to avoid GPU cleanup under the DLL loader lock;
explicit context close still destroys the context and releases its owned resources.
