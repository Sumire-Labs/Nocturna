The gameplay review fixes failures in fog rendering, HUD state, world cleanup
and resource reloads. The review started at `d68c0d27` on `20.1/forge` and keeps
the Java 17 target. It also validates the settings and frame-wait changes that
arrived separately in `d2e5b31f` during the review.

## Findings and fixes

| Trigger | Failure | Fix |
| --- | --- | --- |
| Instanced rain with protected environment fog | The weather program reused the patched fragment shader but supplied neither its independent distance varying nor its fog uniforms. Rain ignored the environment channel. | Share the native fog distance generator and upload both channels when binding vanilla weather. GPU readback checks the resulting rain fragments. |
| A shader pack reads legacy fog uniforms | Render-distance values reached `fogStart`/`fogEnd` instead of environment values. The same problem affected terrain overrides and native draw wrappers. | Export the environment channel during terrain draws through the uniform providers, terrain fog component, weather and native shader-instance wrapper. |
| F3 closes during a long steady-HUD interval | Overlay-only lines remained visible until the timer expired. | Include F3 state in the cache revision and refresh both Extra text and the completed Forge HUD lists immediately. |
| Reduced-debug permissions change during that interval | Cached coordinates remained visible. Open controls could also retain their previous enabled state. | Invalidate on permission changes and refresh the open screen's controls. |
| A world is replaced or disconnected | The heightmap cache retained a `ServerLevel` and chunk future. Greenlight's lazy policy fingerprint also retained its previous client connection. | Clear HUD, chunk, fog and traversal state after the old renderer joins its search thread. Track Greenlight connections with weak references and immutable epoch numbers. |
| A Panini resource graph names an unknown target | Validation happened during drawing, after world redirection, and raised an uncaught exception. Invalid shaders could also be compiled again each frame. | Validate targets before accepting the chain. Cache failed loads until reload or an explicit retry, and verify recovery with a valid resource reload. |
| A touchpad sends fractional wheel deltas | Integer truncation consumed every small event without scrolling. | Accumulate fractional input before advancing rows. |
| A HUD visibility button is activated with the keyboard | Rebuilding the rows removed the focused widget. | Update selection labels and permissions in place. |

The full RSO test also reproduced an identifier collision for
`minecraft:cloud_range` when returning to the classic screen. The canonical
identifier lookup in `d2e5b31f` resolves it. The added cloud-range test checks
that its legacy integer type and native standard identifier survive both
adapter directions; no separate option-specific workaround remains.

## Review and validation

The review followed the custom renderer and compatibility changes through
their callers, including BufferBuilder eligibility, frame fencing, legacy
options and vertex APIs, shader caches, PBR indexing, horizon geometry, FISM,
instanced weather, RSO and Extra. Most fixes concern interactions between those
features. Existing source comparisons remain separate from runtime tests.

The gameplay client creates a world, renders weather, changes HUD controls and
permissions, transfers to the Nether, disconnects and creates a second world.
It then reloads an invalid Panini graph and verifies recovery after restoring
valid resources. The weather test reads actual shader uniforms and pixels;
unfogged fragments fail the test. Further checks cover F3 visibility, retained
world references, Greenlight fingerprints, fractional scrolling and focus.

The broader clients exercise ordinary and compact/async terrain, CCL's real
block callback, IE copper-wire vertices, shader-pack cold/warm reloads, program
binary rejection fallback, GPU matrix uploads, weather expansion, horizon
color/depth and FISM vertex streams. RSO checks include both frontends, native
addon widgets, SDL/GLFW fallback, Unicode, focus, defaults, apply/undo and
reopening. The original Extra GPU and resource-override checks are rerun too.
BrandonsCore is loaded and its mixin targets are audited; these checks do not
exercise every item supplied by an addon.

Reproduce from the repository root with the installed build JDK and Java 17
runtime toolchain:

```powershell
./gradlew.bat -PcompatRuntime :common:test :modern:1.20.1-forge:build
./gradlew.bat -PcompatRuntime :modern:1.20.1-forge:runValidationClient
./gradlew.bat -PcompatRuntime :modern:1.20.1-forge:runRsoValidationClient
./gradlew.bat -PcompatRuntime :modern:1.20.1-forge:runExtraValidationClient
./gradlew.bat -PcompatRuntime :modern:1.20.1-forge:runGameplayValidationClient
./gradlew.bat -PcompatRuntime :modern:1.20.1-forge:runAuditClient
```

The compatibility runtime pins CCL file `5753868`, IE file `4782978` and
BrandonsCore file `5422013`. One initial addon launch stopped in Forge's
NightConfig writer because Windows had locked IE's server configuration file.
The next run loaded the world and completed the addon checks. The old IE build
also logs an unsupported `legacy_unicode` font-provider warning; the checked
CCL and IE rendering paths completed despite it. The deliberately rejected
program binary produces an expected driver error before source compilation
recovers it.

The [machine-readable record](audits/gameplay-review-2026-11-17.json) contains
the final counts, workflow cases and log hashes. Tests run on Windows 11,
Temurin Java 17 and AMD Radeon 8060S/OpenGL 4.6. Physical Linux/macOS behavior
and arbitrary third-party mod or shader packs remain outside this validation.
The recorded workflows have no remaining reproduced failure; they do not
prove that every possible play session is free of defects.
