# Source provenance

Nocturna targets Minecraft **1.20.1 / Forge / Java 17** only.

The renderer and platform implementation are based on [Celeritas](https://git.taumc.org/embeddedt/celeritas),
`stonecutter` commit **26f000239eb8ddf7ee191f0ddf369147e3c92ef6** (2026-11-14).
The imported trees are `common`, `modern`, the MDG plugin, build helpers and Gradle wrapper.
Their original copyright notices and bundled third-party license notices are retained.
The top-level license is LGPL-3.0; see `COPYING` and `COPYING.LESSER`.

This is a Sumire Labs fork, not an official Celeritas release. Report fork-specific problems to this repository.
The prior Embeddium base is preserved in git history through `41a9f33e`.
The `net.caffeinemc.mods.sodium.api` and legacy `me.jellysquid` compatibility sources come from that base.
No additional code was imported directly from newer Sodium releases.

Intentional departures from the pinned Celeritas snapshot:

- A single Forge 1.20.1 target, Java 17 compiler/runtime, `List.get(0)` in place of guarded `List.getFirst()`.
- Public Embeddium version/mod identity and functional adapters for the previous public vertex, option and geometry APIs.
- The local Forge biome getter fix, serializer cache snapshot, collection-based uploads, generic stable radix sort,
  backing-buffer eligibility guard, adaptive CPU/GPU waiting and removal of the old Intel Gen7 startup block.
- Previously accepted Spanish, Korean and Russian translations, merged without discarding new upstream keys.
- Regression tests, a separate validation mod, reproducible build/CI configuration and source audit tooling.
- Angelica's matrix uniform buffer reuse and two-generation render-list pool, from LGPL-3.0 commit
  `d876a203da9b46ee5b5048171b8e5e4deb9bb967`. See `docs/angelica-reuse-backports-2026-11-16.md`.
- Angelica's preprocessing/disk/GL-program caches, from LGPL-3.0 revision
  `996fb54cb05e256ea6039f4e2c8457a221a179b7`, adapted to the existing Celeritas parallel linker.
  See `docs/shader-cache-backport-2026-11-16.md`. Compiled-program saving defaults off as upstream does.
- Angelica's PBR resource index from the same LGPL-3.0 revision, adapted to Forge 1.20.1 pack filters,
  resource-manager replacement and pack lifetime. See `docs/pbr-resource-index-backport-2026-11-17.md`.
- Angelica's instanced rain/snow, weather-column cache and provider batching from the same LGPL-3.0 revision,
  with native Forge biomes, dimension hooks and the bundled shader pipeline. See `docs/angelica-weather-instancing.md`.
- Iris's horizon cone from LGPL-3.0 revision `bff1e69cb6c5519d8745784aa9c8b92984de67e7` (PR #2941),
  using the native Forge 1.20.1 vertex-buffer and shader path. See `docs/iris-horizon-backport-2026-11-17.md`.

Terrain sorting V3, vertex layouts/headers, render-pass consolidation, occlusion, raster culling, shader integration,
batching and the Flywheel shader backend remain Celeritas implementations. Celeritas defaults are retained,
including non-compact vertices, normal-based shading and shadow-only asynchronous occlusion.

The shader implementation is bundled as upstream does. Do not install a second Oculus/Iris renderer alongside it.
Compatibility with arbitrary mixins into removed private Embeddium renderer classes is not promised.
Mesh appenders should use `Context.vertexConsumerProvider()`; the former raw buffer implementation has changed.

See `docs/celeritas-migration-2026-11-16.md` for verification results and compatibility boundaries.
