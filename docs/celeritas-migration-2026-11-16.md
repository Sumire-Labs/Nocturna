# Celeritas base migration — Forge 1.20.1

This records the initial migration at `19ddfceb`. The source manifest is updated by later reviewed changes;
see [the subsequent Angelica reuse ports](angelica-reuse-backports-2026-11-16.md) for the next performance changes.

## Scope and provenance

The sole configured target is `:modern:1.20.1-forge`. The runtime and all product compilation use Java 17.
The Gradle 9.7.1 daemon/build plugins use JDK 25; this does not raise the product's Java requirement.

Upstream: https://git.taumc.org/embeddedt/celeritas, `stonecutter`,
`26f000239eb8ddf7ee191f0ddf369147e3c92ef6`.
The complete `common` and `modern` source trees, build helpers and MDG plugin were imported.
Other game/loader projects are not included in settings. Upstream preprocessor branches remain in the source
so that the implementation can be compared directly, rather than rewriting or simplifying its algorithms.
No code was imported directly from newer Sodium releases.

`scripts/verify-celeritas-base.mjs` checks the pinned clean upstream checkout, every imported file and all additions.
The reviewed hashes are in [the delta manifest](audits/celeritas-migration-delta.json):
**1,623 files unchanged, 32 intentional modifications/removals**, plus the compatibility and test additions.
Use `--record` only after reviewing a changed source delta.

## Preserved Celeritas implementation

Terrain sorting V3, primitive/header-based vertex layouts, async shadow visibility, view-specific render caches,
optional raster culling, render-pass consolidation, batching, shaders and Flywheel's shader backend remain upstream code.
Defaults are Celeritas defaults: compact vertices off, quad-normal shading on, asynchronous occlusion for shadows only,
raster culling off. The old renderer was removed, not combined with these algorithms.
The shader engine is bundled; a separate Oculus/Iris jar must not be installed alongside it.

## Local fixes carried forward

| Fix | Treatment |
| --- | --- |
| Forge biome colors | Read effective effects through `getSpecialEffects()` for grass/foliage. |
| Serializer registry contention (#578) | Immutable published lookup snapshot; synchronized cache growth remains. |
| Upload allocation reduction (#575) | Collect vertex/index uploads once; retain the Stream overload. Preserve Celeritas's per-upload rounded `elementsFor` calculation and optional index arena. |
| Stable generic radix sorting (#580) | Keep the 64-element threshold, stable equal keys, signed-zero and NaN fallback behavior. Terrain V3 is untouched. |
| LRU cleanup (#576) | The early break already exists upstream. Retain Celeritas's capacity and lifecycle. |
| Lazy mesh buffers / layered quads | Retain the newer upstream implementation and primitive layout instead of transplanting the old vertex-only buffers. |
| BufferBuilder eligibility | Preserve Celeritas's fast-format lifecycle and also require non-null backing storage. |
| Adaptive CPU/GPU wait | Forty 1,000 ns flush waits, then yield plus 10,000 ns waits; delete successful fences once, preserve failure exceptions and dynamic limit reevaluation. |
| Intel Gen7 startup check | Remove the call, class and configuration flag together. |
| Accepted translations | Merge old Spanish/Korean/Russian keys while keeping new upstream keys. |
| AMD #549 | The previously reverted workaround is not reinstated. |

## Addon and API compatibility

The previous public API descriptor inventory was extracted from
`embeddium-0.3.32-beta.95+mc1.20.1.jar`, SHA-256
`02cf25d84812bc5ca328d0d0db6be2d9d49611d1150e13173d30061467ab1291`.
`legacy-public-api.txt` freezes **461 class/member records across 73 classes** from the old `org.embeddedt.embeddium.api`,
`net.caffeinemc.mods.sodium.api`, option IDs and option contracts. The ABI test checks actual compiled descriptors,
with four Minecraft override names translated from production SRG names to dev names.
`scripts/ApiInventory.java` regenerates the inventory with ASM on the classpath and the old jar as its first argument.

- Old vertex formats and serializers delegate to the native registry/layout. Adapters work in both directions,
  preserve eligibility checks and retain the legacy `me.jellysquid` vertex entry points. BufferBuilder also supports direct old-interface casts.
- Geometry events retain their old event-bus owner type and Forge dispatch. Old block callbacks receive a live read-only
  view of the current Celeritas meshing snapshot, model data, pose stack and render layer.
- Mesh-appender consumer access remains functional. The old raw-buffer accessor retains its descriptor through a
  carrier exposing the new buffers via `unwrap()`; **the removed old raw buffer/material implementation is not emulated**.
  Addons should write through `vertexConsumerProvider()`.
- Old option bindings, controls, IDs, styled text and group/page/GUI events are connected to the Celeritas GUI.
  Native options are preserved by identity when a legacy event does not replace them, including newer flags.
  Legacy configuration fields are reconciled once at frame start and immediately when legacy GUI changes are applied/saved.
  New-only settings survive old API saves; read-only settings remain read-only.
- CCL, Immersive Engineering and BrandonsCore use the upstream integrations. Rubidium's compatibility mod identity remains available.

This does not promise compatibility with mixins into removed private renderer classes, or arbitrary casts to old
internal mesh builders. Source/binary presence of a public contract also does not certify every addon/version.

## Verification

Environment: Windows 11, Temurin 17.0.20.1 client, Forge 47.3.0, AMD Radeon 8060S,
OpenGL 4.6 driver `26.10.1.261008`. Gradle runs with Temurin 25.

| Check | Result |
| --- | --- |
| `gradlew build` | Product assembly, source jars and unit checks pass. |
| Unit tests | 47 common + 20 Forge tests pass (67 total). Includes original Celeritas tests and carried-fix/API regressions. |
| Published API inventory | All 461 records retained after SRG/dev name normalization; the packaged SRG jar also retains all 461 records directly. |
| Product classfile audit | 2,406 top-level classes target Java 17 or older; licenses/provenance present; validation mod absent. |
| `runAuditClient` | Java 17 startup and complete Mixin target class-loading audit pass. |
| `runValidationClient` | Fresh superflat world, ordinary and compact terrain rebuilds, old block/mesh callbacks, exact old vertex-write bytes and styled legacy GUI/control/events pass. |
| `-PcompatRuntime runValidationClient` | CCL 5753868, IE 4782978, BrandonsCore 5422013 loaded together; world and both vertex modes pass. A registered CCL renderer emits actual quad vertices. |
| IE wire geometry | Real copper connection rendered through upstream `renderSegments`: 256 vertices with compact off and on. |

The validation mod is isolated in `src/validation` and excluded from ordinary client runs and release jars.
It creates disposable worlds only in the development run directory and writes `migration-validation.txt` before exiting.
The Forge headless unit runtime uses a separate unsigned copy of the MDG development game jar.

Known non-fatal warnings also occur in the pinned upstream build: remapping TextureManager's alternative lambda names
and optional `renderOffsetShadow` target, optional Fabric compatibility classes absent on Forge, and IE's old unicode-font
resource. The Mixin audit and tested render paths pass despite those warnings.
Shaderpacks, every graphics vendor, long-running gameplay and arbitrary third-party internal mixins are not exhaustively tested.
No performance improvement percentage is claimed from this migration.

All work remains local on `20.1/forge`; no push or publication is performed.
