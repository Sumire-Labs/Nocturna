## Follow-up implementation

The findings below describe the original review baseline. The follow-up fixes
the confirmed defects and supplies the missing host prerequisites. Configuration,
defaults and the 38 upstream assets remain pinned to `7abb4236`.

| Finding | Resolution | Regression coverage |
| --- | --- | --- |
| Protected fog replaces render fog | Separate environment/render uniforms and distance evaluation, including vanilla shaders | Five protected types in unit tests; real water setup; terrain and vanilla GPU tests |
| Forge hit callbacks bypass particle switch | Cancel the outer Forge operation before extension callbacks | A registered block's real `IClientBlockExtensions` callback stays at zero |
| In-place Panini inherits Scissor | Execute the resource graph with pass-owned state | Reference and in-place red values both 43 with a 1x1 incoming Scissor |
| Target precision is reduced | Copy and compare color/depth/stencil formats | RGBA16F remains RGBA16F (`34842`) |
| Panini JSON and uniforms are ignored | Resource-driven targets/passes/shaders and std140 buffers | Real pack reload; changed JSON default and vertex shader; SamplerInfo/Globals reads |
| Depth shader text is rewritten | Project original world depth, then merge hand depth independently | A whitespace-only depth shader override still merges hand depth 0.2 |
| Cloud percentage follows terrain range | Add the independent native cloud range and camera-distance alpha fade | 128 chunks at 50% stays 1024 blocks across terrain distances |
| Debug screen is reduced | Full catalog, profiles, custom persistence, search, descriptions and permissions | 46 native plus four Extra entries; all text providers and live visualizations |
| Generic effect toggling is blocked | Track spectator ownership separately | A manual effect can load and toggle with prevention enabled |
| Adaptive probe differs | Probe without a tear-extension pre-filter; restore known actual context state | Success/failure/unknown-state tests; Windows client startup |
| Lower dark sky is canceled | Preserve the separate dark-disc draw | The canceled call is limited to the upper sky and End sky |
| Reused macOS windows miss density changes | Update the existing Cocoa OpenGL view after the Forge factory | Compiled against the native ABI; physical macOS validation remains open |

Enabling every visualization also exposed a Forge HUD cache problem: another
renderer appended statistics to an immutable list. Caching now happens at
Forge's completed HUD-list boundary and returns mutable copies. The final
client validation checks its log for swallowed overlay errors as well as test
assertions.

The debug catalog is a host prerequisite, not an import of Minecraft 26.3 or
newer Sodium code. It reads actual 1.20.1 data. The section hierarchy view uses
Celeritas regions and the path view captures its real asynchronous traversal;
this renderer has no vanilla octree. Host version strings and renderer-specific
statistics describe Nocturna's running backend.

The runtime checks cover Java 17, Forge 47.3.0 and AMD Radeon 8060S/OpenGL 4.6 on
Windows. They do not constitute Linux or macOS execution proof, exhaustive
resource-pack validation, or a pixel comparison of every possible scene with
Minecraft 26.3. Source comparison and behavior tests are recorded separately.

## Original review

The implementation at `9ca46fdcae8083d8c7852f686937bffe3f679179` does not have
complete behavior parity with Sodium Extra 0.9.6-beta.4. The earlier integration
report described its validation too broadly. Matching configuration definitions
and passing the existing tests did not establish renderer or UI equivalence.

The reference remains upstream commit
`7abb4236d5d6310633084cd2f613b63fdd1ccb5c`. This review covers its 78 common
Java files, NeoForge registration, assets and the Nocturna adapters, including
the RSO bridge. Product code was not changed during the review.

## Confirmed problems

### P1: protected fog overwrites render-distance fog

`ExtraFog.java:105` copies the protected environment's start/end into the
render-distance start/end. Upstream `FogDistanceHelper.applyProtectedGameplayFog`
changes environmental, sky and cloud channels only. Its render-distance fog
remains active independently. The port's scalar terrain shader cannot express
those two independent channels.

The client probe seeded a reference render end of 64 and called the actual
ported helper, whose protected-fog transformation matches upstream. It then
called the real mixed-in 1.20.1 `FogRenderer.setupFog` with the same view distance.

| Protected water setting | Reference render end | Port render end and GL fog end |
| --- | ---: | ---: |
| 256 | 64 | 256 |
| Off (-1) | 64 | `Float.MAX_VALUE` |

Off therefore removes render-distance fog as well as protected environment fog.
A large protected distance also lets terrain stay unfogged beyond the original
render-distance fog end. Restoring parity requires separate environment and
render-distance channels through setup, shader uniforms and distance evaluation.

### P2: custom Forge block-hit effects bypass the switch

`MixinBlockParticles.java:18` intercepts `ParticleEngine.crack`. Forge's outer
`addBlockHitEffects` calls `IClientBlockExtensions.addHitEffects` first, and calls
`crack` only if the extension declines to handle the effect.

With global particles enabled and `blockBreaking=false`, the review block's
extension still ran once and successfully created a flame particle. The
upstream hook cancels the outer `ClientLevel.addBreakingParticles` operation.
The port must cover the Forge extension path before that callback runs.

### P2: the in-place Panini copy inherits Scissor

`PaniniProjection.projectColor`, at line 362, blits the temporary color target
back into the source/output target without controlling Scissor. `PaniniProgram.draw`
temporarily disables Scissor, then restores it before the blit. An existing
Scissor rectangle clips the copy at 100% quality and also affects an in-place
outline-mask projection.

The GPU probe compared out-of-place projection with in-place projection of
the same gradient. With a 1x1 Scissor rectangle, the tested pixel's red channel
was 43 in the reference output and 32 in the in-place output. GL reported no
error; the latter pixel retained its unprojected input value. Both paths must
produce the same image independently of incoming Scissor state.

### P2: Panini targets lose the source format

Upstream `PaniniProjection.createTarget` copies color and depth formats and
includes them in its reuse check. The port's line 304 creates a plain 1.20.1
`RenderTarget`; its reuse check considers size, depth presence and stencil
presence only.

The GL probe supplied an RGBA16F model target (`34842`). The created target was
RGBA8 (`32856`). This reduces precision and clamps HDR values when a compatible
mod supplies a floating-point target. Default vanilla RGBA8 tests do not cover
that case.

### P2: the Panini resource pipeline is partially replaced

Upstream obtains the `sodium-extra:panini` post chain through the shader manager.
The port ships `post_effect/panini.json` but never executes its graph. It hardcodes
the full-screen vertex shader and the color/depth programs instead.

`PaniniProgram.fragment`, at line 60, removes the `SamplerInfo` uniform block
without supplying `InSize` or `OutSize`. A resource-pack shader that uses those
standard fields fails compilation. A replacement vertex shader or pass graph
in the JSON has no effect.

The depth/hand combination also replaces one exact GLSL statement at line 29.
Changing only whitespace in that statement leaves valid GLSL but prevents the
replacement. The generated program can compile with an unused hand-depth
sampler and omit hand-depth integration. These resource-override cases were
confirmed by source inspection; they were not GPU-tested in this review.

### P2: cloud-fog percentage uses a different distance

Upstream computes the endpoint from `Minecraft.options.cloudRange()`, independently
of terrain fog. `ExtraFog.cloudRange`, at line 30, derives a substitute from the
current terrain fog end. The shader adapter also retains the old start when
that endpoint does not change, instead of consistently using the original
cloud fade convention.

For example, a source cloud range of 128 chunks gives a 50% endpoint of 1024
blocks. A port terrain end of 64 gives 32 blocks. The example compares equal
percentage settings with explicit native range inputs; it is not a measurement
of the original 26.3 client. Minecraft 1.20.1 lacks the newer independent cloud
range. Substituting terrain fog changes the feature's meaning and needs an
explicit prerequisite port to meet the requested equivalence.

### P2: More HUD Overlays opens a simplified replacement

Upstream opens Minecraft's `DebugOptionsScreen`, and its mixin supplies labels,
permission tooltips and descriptions for Extra entries within that screen.
The port's `ExtraDebugOptionsScreen`, at line 94, builds four Extra-only cycle
controls. It supplies neither entry-description tooltips nor the original
screen's profile handling and standard debug-entry management.

The four Extra visibility choices and the default light-update warning exist.
The screen as a whole is a reduced substitute. This is a missing newer-Minecraft
prerequisite, not a completed port of that UI.

## Other differences and unresolved cases

* Spectator prevention intercepts the generic 1.20.1 `togglePostEffect` method,
  while upstream targets `toggleSpectatorPostEffect`. The port allows manual
  `loadEffect` calls but cancels toggling their result when prevention is on.
  The old single-effect API needs separate spectator ownership tracking to
  preserve the newer distinction. This was checked statically.
* Adaptive VSync adds a WGL/GLX/EGL extension-name filter before trying the
  interval. Upstream tries SDL's operation and restores the queried interval.
  The port queries actual Windows state but uses its own tracked/default value
  on other platforms. Cross-platform capability and restoration equivalence
  remain unverified.
* The port disables the lower dark-sky draw under `sky=false`; upstream's Extra
  mixin explicitly targets `renderSkyDisc` and `renderEndSky`. Related Celeritas
  source has a separate `renderDarkDisc` phase. The exact 26.3 call mapping still
  needs verification before treating this as equivalent or a confirmed bug.
* macOS density is applied before Forge's `setupMinecraftWindow` factory. Whether
  that factory reuses an existing window matters to a creation-only hint.
  No macOS runtime proof was obtained.
* Greenlight's full runtime is bundled by Nocturna. Upstream lists the runtime
  as optional. The port therefore follows the upstream-with-Greenlight case,
  rather than the upstream-API-only case, when a server supplies a valid policy.
  This is an integration choice, not a missing feature.

## Areas without a new discrepancy found

The existing source verifier still passes the full option-definition comparison,
the retained configuration/default logic, FPS statistics, config I/O, particle
map lookup, toast predicates, identifier adaptation, value formatters, utility
code, Iris compatibility helper, animation categories and all 38 assets.

The remaining hooks were traced for animation timing, particle categories,
rain/snow cancellation, sky bodies, biome/fog colors, cloud height, entity/name
tags, block entities, light updates, instant sneak, HUD timing, steady debug text,
toast removal and RSO typed controls/default reset. Their presence and traced
conditions do not prove pixel or third-party-mod parity in every scene.

The three omitted vertex declarations still have no callers in the pinned
upstream tree. Their omission does not account for the issues above.

## Validation limits and reproduction

The old verifier checks whether mapped port files exist for most entries. It
compares nine retained implementation files and the option DSL, and explicitly
excludes the adaptive-sync implementation from its token comparison. It does
not compare most rewritten renderer adapters. A passing count of 78 mappings
must not be presented as proof of 78 equivalent implementations.

The new probe ran on Java 17, Forge 47.3.0 and the AMD Radeon 8060S/OpenGL 4.6
driver. Its results are in `docs/audits/sodium-extra-parity-probes.json`.
The local review-only harness is under `.git/research/extra-parity-review`;
the Gradle init script substitutes the validation source root, without changing
the product source roots:

```powershell
./gradlew.bat -I .git/research/extra-parity-review/review.init.gradle :modern:1.20.1-forge:runExtraValidationClient
```

No implementation fix, implementation commit or push was made during this
review. The protected-fog channels should be corrected first, followed by the
confirmed rendering/Forge-hook failures and the incomplete resource/UI ports.
