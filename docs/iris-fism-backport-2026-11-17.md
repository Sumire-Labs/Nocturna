# Iris FISM shadow-pass optimizations for Forge 1.20.1

The shadow pass now skips sign text, additional banner/shield patterns, item enchantment glint and map rendering.
Normal rendering keeps the native paths. Banner cloth, shield geometry and the dyed base layer remain present.

The source is LGPL-3.0 Iris commit
[`82e39b0252a9576190502001ccbadb038c6b5e78`](https://github.com/IrisShaders/Iris/commit/82e39b0252a9576190502001ccbadb038c6b5e78).
The port targets Minecraft 1.20.1 Forge and Java 17. No newer Sodium code or Minecraft rendering backend is imported.

## Behavior and platform mapping

All four mixins use the original `IrisApiV0Impl.isRenderingShadowPass` predicate, which resolves to
the existing `ShadowRenderer.ACTIVE` state. They run only in the client distribution.

- `SignRenderer.renderSignText` corresponds to the newer `AbstractSignRenderer.submitSignText`.
  Cancelling that method covers both sign faces and the inherited hanging-sign implementation while retaining
  the sign model. Glowing text and outlines are suppressed with the text during shadows.
- `BannerRenderer.renderPatterns` retains the plain substrate and one dyed BASE layer. In newer Minecraft,
  the substrate is submitted by the caller and the base dye is a separate argument. In 1.20.1, both are inside
  `renderPatterns`, and the dye is encoded as a BASE entry in its list. The adapter keeps both native draws,
  selects the banner or shield base material, preserves pose/light/overlay/color, and skips extra patterns.
  It searches for BASE rather than assuming list order. A legacy list with no BASE retains its plain substrate.
- `ItemStack.hasFoil` returns false immediately during shadows, including for mod item overrides. It leaves
  the item and its NBT unchanged and uses the normal result outside shadows.
- `MapRenderer.render` is cancelled before map-instance creation, texture upload, icons or labels. The separate
  update method remains available, and a pending update is consumed when normal map rendering next occurs.
  The item frame and map background are outside this method and retain their native rendering.

The original sign/map/foil cancellation bodies are checked against the pinned source by
`scripts/verify-iris-fism-port.mjs`. The banner adaptation was checked against the native 1.20.1 source and
Mojang's 26.1 client bytecode. This preserves the original base-layer behavior across the API change.

The mixins share the default-enabled `mixin.features.shader.shadow_pass` rule in `embeddium-mixins.properties`.
The source sets and public mod IDs are unchanged.

## Verification

Tests ran in an isolated worktree on the committed renderer and RSO base, without the shared workspace's
unfinished Extra or shader-transform changes.

- All 120 unit tests passed: 55 common and 65 modern, with no failures or skips.
- The real Forge client checked 192 banner/shield combinations: both model types, all 16 dye colors,
  one/three/twenty pattern entries and explicit glint on/off. Complete material and vertex streams matched
  the native reference containing only the substrate and dyed BASE. Positions, color, UVs, light, overlay
  and normals are compared. Normal output matched before and after the shadow pass.
- Reordered BASE entries and empty legacy pattern lists were also checked.
- Standing, wall and hanging signs were tested with normal and glowing text on both faces. Their shadow
  streams matched the text-free model reference exactly, and normal text output recovered unchanged.
- A registered validation item checked that its foil override runs normally, is bypassed during shadows,
  and resumes afterward. Enchanted-item NBT remained unchanged.
- Map tests checked suppression before texture creation, preservation of pending updates, normal upload,
  markers/labels, and restoration of the normal vertex stream after shadows.
- A synthetic shader pack enabled an actual Iris shadow renderer. The validation hook observed 1,858 real
  shadow passes and checked the foil predicate and retained banner base inside the active pass.
- Existing GPU horizon/PBR/cache, weather, legacy API, compact-vertex and asynchronous-occlusion checks
  passed with CCL, Immersive Engineering and BrandonsCore loaded.
- The product passed the Java 17 class-version check. The validation item, test mod and test addons are excluded.

```text
node scripts/verify-iris-fism-port.mjs <Iris checkout>
node scripts/verify-celeritas-base.mjs <Celeritas checkout>
gradlew.bat --gradle-user-home D:/DevCache/Gradle/celeritas-audit build :modern:1.20.1-forge:compileValidationJava --no-daemon
gradlew.bat --gradle-user-home D:/DevCache/Gradle/celeritas-audit -PcompatRuntime :modern:1.20.1-forge:runValidationClient --no-daemon
modern/versions/1.20.1-forge/run/fism-validation.txt
modern/versions/1.20.1-forge/run/migration-validation.txt
```

Gradle runs on JDK 25; product compilation, unit tests and the game use Java 17. These changes adopt Iris's
decorative-shadow policy, including its effect on shader packs that observe decorative color. The tests verify
that policy and the native base geometry; they do not establish pixel equivalence for every third-party pack.
No FPS or frame-time improvement has been measured.
