# Iris horizon mesh for Forge 1.20.1

The horizon now uses Iris's inverted octagonal cone: 10 vertices and eight triangles. The previous prism
and two tiled planes used 1,384 vertices. The shader interface, render targets, matrices and fog/color
setup remain the native Nocturna implementation for Minecraft 1.20.1 Forge and Java 17.

## Source and adaptation

The geometry comes from LGPL-3.0 Iris revision `bff1e69cb6c5519d8745784aa9c8b92984de67e7`, including
[PR #2941](https://github.com/IrisShaders/Iris/pull/2941), merged as
`2b6c21fae1c3f88a4b73b996d4a0a8d7555dc8df`.

The port preserves the bottom-center vertex, all nine perimeter vertices, winding, heights of -16 and 16,
radius clamp of 256, float angle rounding and sin/cos evaluation. The closing perimeter vertex retains
the original rounding difference from the first. The source audit checks the complete geometry method
and constants against the pinned original, including both frozen test references.

Minecraft 1.20.1 already supports `TRIANGLE_FAN`. Its `VertexConsumer.vertex(...).endVertex()` replaces
the newer `addVertex(...)` API. The native `VertexBuffer` draws the fan with ten sequential indices,
which produces the eight triangles checked against an explicitly expanded triangle reference.
No newer Minecraft GPU abstraction or Sodium code is imported.

As in Iris, the buffer is built once, replaced when effective render distance changes, and closed when
the renderer is destroyed. Rebuilds still occur above the radius clamp when the configured distance
changes. The public constructor, `renderHorizon` and `destroy` signatures remain unchanged.

This adopts the newer Iris sky geometry. Shader packs that inspect sky vertex positions may display
differences from the old prism. The comparison target is the pinned Iris geometry.

## Verification

Validation used an isolated worktree containing this change on top of the committed PBR and weather ports.

- All 119 unit tests passed: 55 common and 64 modern, with no failures or skips. The three new tests
  compare raw float bits for 270 radii, check the native 120-byte fan buffer, and cover the clamp and seam.
- The real Java 17 Forge client completed 384 exact color and depth comparisons between the production
  renderer and the frozen Iris geometry expanded into indexed triangles. Cases cover render distances
  2/8/16/32, six yaw angles, two pitch angles, two FOVs, culling on/off and two shader variants.
- The shader variants exercise position-dependent color, fog, vertex deformation, vertex IDs, primitive
  IDs and flat interpolation. The fixture verifies that geometry was drawn, rather than comparing empty frames.
- GPU buffer readback matches the original vertex bytes. Unchanged distance reuses the buffer; changed
  distance closes and replaces it. Destruction invalidates the final buffer.
- The normal validation world rendered the production horizon with an active Iris shader pack. Existing
  shader-cache, PBR, weather, legacy API, compact-vertex and asynchronous-occlusion checks also passed,
  with CCL, Immersive Engineering and BrandonsCore loaded.
- The packaged product passed the Java 17 class-version check. Validation classes and test addons are excluded.

Commands and client reports:

```text
node scripts/verify-iris-horizon-port.mjs <Iris checkout>
node scripts/verify-celeritas-base.mjs <Celeritas checkout>
gradlew.bat --gradle-user-home D:/DevCache/Gradle/celeritas-audit build :modern:1.20.1-forge:compileValidationJava --no-daemon
gradlew.bat --gradle-user-home D:/DevCache/Gradle/celeritas-audit -PcompatRuntime :modern:1.20.1-forge:runValidationClient --no-daemon
modern/versions/1.20.1-forge/run/horizon-validation.txt
modern/versions/1.20.1-forge/run/migration-validation.txt
```

Gradle runs on JDK 25; product compilation, unit tests and the game use Java 17. The original PR addressed
a performance regression on Minecraft 1.21.5+. The smaller mesh is verified here; an FPS improvement on
1.20.1 has not been measured. These fixtures do not establish compatibility with every third-party shader pack.
