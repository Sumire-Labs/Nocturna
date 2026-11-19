# Temporal FSR integration (2026-11-19)

Nocturna retains Minecraft 1.20.1 Forge, Java 17 and LGPL-3.0. The new optional Windows x64
backend calls the official AMD FSR SDK rather than reimplementing its upscaling algorithm.
It explicitly selects the requested 2.3.4, 3.1.6 or 4.1.2 provider. This is upscaling, not frame generation.

## Source and runtime boundary

Super Resolution development revision `85902b20444644d889a4051c8aaa814f77033d29` was reviewed for
architecture: an OpenGL game can share textures and synchronization with a native API used by
an upscaler. Its GPL Java/C++ algorithm integration was not copied. Nocturna's bridge and integration
are independently written LGPL-3.0-or-later code. The four AMD API headers are unmodified MIT files
from [SDK revision 7539ae8](https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/tree/7539ae8b6568ad29ed78a753404351daf3b5cca7).
The bridge's source, headers, build script and manifest are included in the sources jar.

AMD's ML implementation remains a proprietary signed external DLL; it is not made open source by
this integration and is not shipped in Nocturna jars. The optional installer downloads the exact
upstream binary, checks its SHA-256 and AMD signature, and saves its license alongside it.
The game makes no automatic network requests and checks the pinned hash before loading the DLL.

```powershell
./scripts/Install-FsrRuntime.ps1 -GameDirectory 'C:/path/to/minecraft-instance'
```

The pinned runtime SHA-256 is `aad4fccccb29084a655f9a4f9f613bd22c8c03fb4a0f8840148cc6543b4ccace`.
For the optional native path, OpenGL 4.5 with `GL_EXT_memory_object_win32` and
`GL_EXT_semaphore_win32`, a compatible D3D12 GPU/driver and the external runtime are required.
The GL adapter LUID selects the same D3D12 adapter. Linked-node configurations are rejected.
Other platforms, missing extensions, missing/modified runtime, unavailable providers and failed
GPU output checks use the existing FSR 1 path without enabling temporal jitter.

The default remains FSR 1.0 and the existing `FSR` configuration value is preserved. The additional
`FSR2`, `FSR3`, `FSR4_1` values are exposed in the upscaling selector. FSR 1 sharpness keeps its
existing stops mapping; the new providers receive the SDK's normalized sharpness value.

## Shader-pack contract v1

Ordinary Iris shader packs are **not** automatically converted to temporal FSR. Guessing a velocity
buffer, applying a second TAA or replacing a pack's jitter would change its rendering semantics.
The existing Iris uniforms, projections, passes, render-target flips and TAA remain unchanged.
Only a pack with the full explicit contract receives the two additional uniforms.

Add the following to the preprocessed `shaders.properties` only when the pack implements it:

```properties
nocturna.upscaling.version=1
nocturna.upscaling.colorSpace=srgb
nocturna.upscaling.motionVectors=colortex7
nocturna.upscaling.motionConvention=previousMinusCurrentUnjitteredUv
nocturna.upscaling.jitter=nocturnaTemporalJitter
nocturna.upscaling.replacesTaa=nocturnaTemporalEnabled
```

`colortex7` may be replaced by an existing `colortex0` through `colortex15`, provided it is a
full render-resolution RG16F/RG32F/RGBA16F/RGBA32F velocity buffer. It must contain current-to-previous
motion in **unjittered OpenGL UV** coordinates (`previousUv - currentUv`), including camera motion
and independently moving geometry. It is read from the same final ping-pong side that Iris uses,
with normal per-frame parity. Depth is the scaled world target's ordinary OpenGL [0,1] depth.
Near/far planes and vertical FOV are derived from Iris's actual captured perspective matrix,
not guessed from render distance or the pack's nominal `far` uniform. Packs with different depth conventions
must not opt into v1. Writing only camera motion is not a complete adapter for animated entities.

When `uniform bool nocturnaTemporalEnabled` is true, the pack must apply
`uniform vec2 nocturnaTemporalJitter` (input pixel units, +Y upward) consistently to world rendering:

```glsl
gl_Position.xy += nocturnaTemporalJitter * 2.0 / vec2(viewWidth, viewHeight) * gl_Position.w;
```

The offsets and phase count come from the **selected official SDK provider's query functions**.
Do not apply another jitter sequence, include jitter in the declared velocity, or run a separate
TAA while enabled. When false, use the pack's ordinary rendering path. A shader reload, dimension
change, resize or settings change destroys/recreates the native context as necessary. A first frame
after target creation/resize may use the original path until Iris has prepared matching targets.

v1 resolves the sRGB display-referred world image at the existing RenderScale boundary: after
the pack's final/color-space pass, before native-resolution outlines, vanilla post-processing and GUI.
The transport converts sRGB to linear floating-point input for the official upscaler, flips GL/D3D
image/velocity/jitter Y conventions, and converts the output back to sRGB. Only sRGB display output
is accepted. All three providers run the real SDK's temporal algorithm with automatic exposure;
the API's HDR input flag describes the linear float transport.

This insertion point preserves the existing RenderScale pipeline, but does not move a pack's bloom,
film grain, depth of field or other post effects to native resolution. It also cannot recover HDR
information already lost by the pack's final SDR pass. AMD recommends inserting upscaling earlier
for the best quality; that requires a future per-pass/HDR pack contract, not an automatic rewrite
of existing packs. Optional reactive/transparency mask inputs and frame generation are not part of v1.
The interface is Nocturna's contract, not an implementation of Super Resolution's separate pack API.

## Synchronization and failure handling

Three shared texture/command-allocator sets allow work in flight. A D3D12 fence imported into GL
orders GL input writes, the D3D dispatch and GL output sampling; textures return to COMMON/GENERAL
before crossing API boundaries. Native NT texture/fence handles outlive their GL imports, and the
entire FFX creation descriptor chain outlives the effect context. Destruction waits for GPU users.

GL programs, 2D bindings, other texture targets, samplers, image bindings, active unit, framebuffer
bindings and affected enable/pixel-transfer states are preserved. No per-frame GPU readback is used.
Context creation performs one output check before exposing enabled=true. In testing, a 64x64 FSR4
input was accepted by the SDK but produced black; that configuration is rejected safely. Tested
ordinary and odd resolutions pass. This is an observed driver/runtime limit, not a claimed SDK-wide
minimum. Frame time uses `System.nanoTime`, independently of Iris's unchanged timer. History resets
on creation, discontinuous camera/FOV changes and long gaps.

## Validation

Real-driver fixtures and the Minecraft shader-pack probes are kept separate from the product jar.
The integration fixture under `scripts/fixtures/temporal-fsr-pack` demonstrates the contract for a
static scene with camera reprojection. It does not claim complete animated-entity velocity support.
Primary references are AMD's [FSR4 integration guide](https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/blob/7539ae8b6568ad29ed78a753404351daf3b5cca7/Kits/FidelityFX/docs/techniques/super-resolution-ml.md)
and Khronos's [Win32 external-object specification](https://registry.khronos.org/OpenGL/extensions/EXT/EXT_external_objects_win32.txt).

Results are recorded in `docs/audits/temporal-fsr-2026-11-19.json`. Tests ran on Windows, Radeon
8060S / OpenGL 4.6 driver `26.10.1.261008`, with the Minecraft clients on Java `17.0.20.1`.
The accepted client runs contain 82 captures across 13 runs, all with zero OpenGL errors.
Shader-off checks inspect backend inactivity; live program uniform checks apply when the Iris pipeline exists.

| Check | Result |
| --- | --- |
| Common + modern unit tests | 194 tests, zero failures/errors |
| Product Java 17 bytecode | 4,520 classes checked; AMD effect DLL absent from the jar |
| FSR native source/binary integrity | Seven normalized-source/binary hashes verified during resource processing |
| Bridge rebuilt from the sources jar | Byte-identical DLL, SHA-256 `b4415a70ca79f761dc670715bb01298cdd39cd83692e66bd3706649c47530cfc` |
| Direct official SDK execution | 2.3.4 / 3.1.6 / 4.1.2: 12 frames each, finite expected constant-color pixels |
| Production GL conversion/interop/resolve | Gradient orientation, sRGB round trip, hostile GL state, allocator-ring reuse and history reset passed |
| Odd dimensions | FSR4 at 641x361 -> 1282x722 passed |
| Tiny-input failure path | This driver's invalid 64x64 FSR4 output rejected before enabling pack jitter |
| Minecraft temporal fixture | All three providers active for dry/rain/snow and warm/source reloads; GL errors zero |
| FSR4 lifecycle | Native/scaled resolution, spatial/temporal filter, shaders off/on: actual program enabled/jitter uniforms match the backend state |
| Missing AMD runtime | Actual shader enabled=0, jitter=(0,0), ordinary FSR1 rendering remains available |
| Existing shader packs | Eight packs pass all six scene/reload captures using the FSR1 fallback, with temporal rendering disabled |
| Photon elevated sky | Y offset +124: dry/rain/snow captures pass; reviewed sky has no black band |

The eight existing packs are Complementary Reimagined 5.9.3, Complementary Unbound 5.9.3, BSL
10.1.8, Photon 1.4, Solas 3.9, MakeUp Ultra Fast 9.6, Rethinking Voxels 0.1-beta9 and Mellow 3.5.
Their input archives are checked against the existing SHA-512 manifest. All eight dry captures
and the elevated Photon sky were visually reviewed. They remain ordinary Iris packs using
FSR1; these tests do not claim they have acquired motion-vector adapters or FSR4 rendering.
The source and warm reload probes report zero additional stage compilations with the existing
program reuse/cache implementation. Capture/probe classes are not shipped in the product jar.
No FPS improvement, universal GPU support, exhaustive pack-option coverage or temporal quality
of arbitrary third-party adapters is claimed by these correctness tests.

Rebuild and run the isolated Java 17 driver tests:

```powershell
./scripts/Build-FsrBridge.ps1 -JavaHome 'C:/path/to/jdk-17'
./scripts/Test-FsrInterop.ps1 -JavaHome 'C:/path/to/jdk-17' -Runtime 'C:/path/to/amd_fidelityfx_upscaler_dx12.dll'
./gradlew.bat :common:test :modern:1.20.1-forge:build
```

The full native Minecraft fixture uses `scripts/shader-probe.init.gradle`, a ZIP containing
`scripts/fixtures/temporal-fsr-pack/shaders`, and these properties:

```text
-PshaderProbeSources=<repo>/modern/src/shaderProbe
-PshaderProbePack=<fixture.zip>
-PshaderProbeResult=<result.json>
-PshaderProbeUpscaleMode=FSR4_1
-PshaderProbeRenderScale=QUALITY
-PshaderProbeExpectTemporal=active
-PshaderProbeTemporalLifecycle=true
-PshaderProbeFsrRuntime=<amd_fidelityfx_upscaler_dx12.dll>
:modern:1.20.1-forge:runShaderProbeClient
```

Use `FSR2` / `FSR3` without the lifecycle property for their scene/reload checks. A deliberately
missing runtime with `shaderProbeExpectTemporal=fallback` checks the failure path. For the existing
packs, run `scripts/Test-ShaderPacks.ps1` with `-UpscaleMode FSR4_1 -RenderScale QUALITY
-ExpectTemporal fallback`. The existing default probe remains usable against renderers without
Nocturna's optional temporal API. Runtime installation while the game is open requires a shader
reload or changing the upscaling filter before retrying a previously unavailable backend.

## Follow-up implementation review

The table above records the original integration at `f8c8a04b`. The follow-up review and current
bridge hashes are recorded in `docs/audits/temporal-fsr-review-2026-11-19.json` and
`native/fsr/source-manifest.json`.

The review corrected four issues:

* An already installed DLL caused the installer to return before repairing its missing license.
  Both files now have pinned hashes, both are validated before publishing a new DLL, and retries
  repair missing or modified license files. Offline-complete installs make no network requests.
* A closed wrapper's `jitter` call could pass a null pointer into JNI. Native contexts now use
  checked opaque IDs, and stale/foreign IDs and wrong-context calls are rejected. Java owners
  remain idempotently closeable; JNI string copying no longer pins a Java string across native allocation.
* A fence's device-removed sentinel `UINT64_MAX` was treated as a completed submission.
  The shared wait implementation now rejects it. A CPU-only protocol test exercises this and
  event/timeout/post-wait failures; it does not remove the real GPU device.
* Non-finite perspective markers, and depth planes that overflow or underflow on conversion to
  the SDK's float fields, could pass the camera helper. They are rejected before SDK dispatch.

The suspected pixel-unpack byte-swap interaction did not reproduce and is not a rendering fix.
The enlarged real-driver fixture preserves it as a regression check. A separate moving XY scene
compares the production GL conversion with independently CPU-prepared inputs to the same official
SDK providers; all three providers match at the sampled pixels (zero difference out of 255).
This tests transport and orientation; it is not an independent implementation of AMD's ML algorithm.

Windows CI now compiles the bridge and runs the fence protocol test. The existing Linux snapshot
build continues checking Java 17 bytecode, product packaging and all source/binary manifest hashes.
The native source/header additions are included in the sources jar. AMD's binary remains external.
The repeat review passed 200 unit tests, the eight installer cases, the native fence protocol test,
all three real-driver moving-scene comparisons, and 82 error-free captures across 13 client runs.
The regenerated bridge was also rebuilt byte-identically from the sources jar. This integration
includes the separately added `ae3b044e` uniform-supplier optimization, which was preserved.
