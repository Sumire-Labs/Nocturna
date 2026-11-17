# Reese's Sodium Options 2.2.5 backport

Nocturna embeds the MIT-licensed [RSO release `mc26.3-2.2.5`](https://github.com/FlashyReese/reeses-sodium-options/tree/mc26.3-2.2.5),
commit `7093d1d50514125cf933cd02e1336c69a3147289`. The target remains Minecraft 1.20.1, Forge and Java 17.
The requested baseline is RSO without Controlify installed.

All 62 non-Controlify client Java sources and the original icons and 18 translations are included.
The three Sodium mixin hooks are connected to Nocturna's screen factory, option metadata and mutable control bounds.
`scripts/verify-rso-base.mjs` checks the source revision and hashes of all 92 upstream source, resource and license entries.
Its manifest records the four excluded Controlify entries and each replacement hook.

## UI and bindings

The imported implementation handles ranked search, result navigation, grouped tabs, collapsible option groups,
themes, tooltips, visibility preferences, reduced motion, reset and undo actions, keyboard focus and narration.
The RSO preferences page and JSON configuration retain the upstream defaults and persistence code.
Nocturna supplies its existing option pages, addon construction events, storage handlers and reload flags.
Existing Minecraft and Nocturna bindings provide the actual default values for reset actions.

Custom legacy controls retain their rendering, mouse input, keyboard input and edit transaction.
Their bounds update in place, including resized built-in slider/checkbox geometry; the control instance,
focus and in-progress editing state survive layout changes. Legacy standard controls use the original RSO rows.
Addons can provide dynamic names, tooltips, defaults, availability, control visibility and validators through
`org.embeddedt.embeddium.api.options.RsoOptionMetadata`. `RsoModTheme` supplies per-mod ARGB palettes.
The default palette and five presets match the reference runtime's public output.
Arbitrary custom controls retain RSO's original fallback behavior, including its absence of action overlays.

When RSO is disabled, the classic Nocturna menu still exposes every RSO preference, including the enable switch.
Opening the former Nocturna screen class directly also selects RSO when enabled. Shader-pack selection continues
to open the bundled shader menu.

Minecraft 1.20.1 drawing, focus, input and narration APIs are adapted at the platform boundary.
Horizontal wheel values reach child controls; Tab and Shift+Tab use the event's modifiers and wrap correctly.
SDL's public text-input API wraps the existing GLFW window on Windows, X11/XWayland and macOS. The renderer
and GLFW context remain owned by Minecraft. SDL input is routed through Minecraft/Forge callbacks, while
duplicate GLFW keyboard/character events are suppressed only during an RSO screen's input session.
Candidate placement uses window coordinates, including the framebuffer/window scale on Retina displays.
The SDL native libraries are from LWJGL 3.4.3, source revision `147a8ee32dbf9ac02f3794964490687b6bbda1bc`.
Only the SDL native libraries are embedded; Minecraft's LWJGL Java classes are retained.

Real GLFW/OpenGL input-session checks pass on Windows 11 and Ubuntu 24.04 through WSLg/XWayland.
They check the caret rectangle, UTF-8 events, keyboard routing and focus/session teardown while preserving
the host window, context and framebuffer. These are native API/dispatch tests, not a manual IME conversion
or dictionary-candidate test. macOS x64/ARM64 code and native libraries are included but have not been run:
neither the local environment nor the user has an available Mac. The optional manual CI workflow and
`scripts/run-rso-native-input.mjs` provide the same native-window checks for a future Mac runner.

The internal configuration bridge implements the contracts consumed by RSO. It does not expose the newer
Sodium configuration API to addons. No newer Sodium implementation sources are included.
The prompt and fingerprint compatibility helpers come from the previous LGPL Embeddium code.
Their button bounds and Escape/Tab/Enter/Space responses match the reference prompt in executable traces.
Original MIT RSO button narration output and priority also match with and without keyboard focus.

## Java 17 search behavior

RSO's release runs on Java 25. Java 17 has older Unicode tables, which change normalization and matching for
newer characters. ICU4J 76.1 supplies Unicode 16 normalization, casing and character categories in a private
package. Both development and production use that private copy; Minecraft keeps its existing ICU library.
The original RSO ranking, n-gram generation and edit-distance algorithms remain in use; their Unicode
operations use the Java 25 tables through the adapter.

`scripts/verify-rso-search.mjs` runs the unmodified release search sources on Java 25 and the backport on Java 17.
The trace covers normalization and n-grams for every Unicode scalar, contextual normalization examples and
6,288 searches with several result limits, diacritic folding and reranking settings. Queries include characters
added after Java 17's Unicode tables. Normalized output, n-grams, result order and score bits match:

```text
normalization=d5fbcf8fb1cedc7d2b050cbe09d86998540278fb90e14c11d031d0c81b553628
grams=0cbe33420f46060610bf8ea00bb6aec442e76190e6d011d02f6991a4e034957a
ranking=977722d1703ca9198ffebc3b4196e8443a17e40a723db080b0e6b9399785766e
```

The stepped-value regression fixture was recorded by executing the public validator API used by RSO's
Sodium 0.9.2 dependency. Off-grid and out-of-range values fall back to the default instead of rounding.

## Verification

The separate `runRsoValidationClient` test mod opens the real Forge screen on Java 17. It exercises option
registration, legacy events, defaults, per-option and global undo, applying and saving, Unicode and clipboard
input, keyboard controls, custom legacy controls, group collapse, narration, resizing, preference rebuilds and
disabling/re-enabling RSO. It writes `rso-validation.txt` and a screen capture in the run directory.
The validation mod and fixtures are excluded from the product JAR.

Verified on Windows 11 with Forge 47.3.0 and Java 17:

| Check | Result |
| --- | --- |
| Common and Forge unit tests | 174 passed |
| Java 17 product class audit | 4,505 classes; no class version above 61; validation/reference classes excluded |
| RSO client | 240 options with the metadata fixture; live defaults, legacy transactions, horizontal input, Tab wrap and SDL/Forge dispatch passed |
| Renderer and addon integration client | Passed, including compact vertices, asynchronous terrain, legacy geometry hooks and weather |
| Full shader-pack warm reload | Zero stage compiles, including terrain programs |

Seven whole-screen comparisons against the original MIT RSO sources adapted to the same Java17/1.20.1 host
have zero differing pixels: general, quality, RSO preferences, search, narrow layout, pending edits and themes off.
Each comparison contains 409,920 pixels. The recorded host patch maps APIs and Java syntax; reference classes
are generated into the validation source set and never shipped. Both variants share the platform adapter,
which is separately checked against public reference API outputs and native input tests. These comparisons
do not assert identical Minecraft 26.3 and 1.20.1 fonts or operating-system candidate-window appearance.

Enable that comparison with `-PrsoReferenceSource=<checkout-of-mc26.3-2.2.5>` when running
`:modern:1.20.1-forge:runRsoValidationClient`. Executable palette, prompt and narration contract comparisons
are available through `scripts/verify-rso-ui-contracts.mjs`.

Example metadata for an existing addon option:

```java
new RsoOptionMetadata<Boolean>()
    .defaultValue(() -> true)
    .tooltip(() -> Component.literal(option.getValue() ? "Enabled" : "Disabled"))
    .register(new ResourceLocation("example", "option"));
```

`RsoOptionMetadata.pendingValue(id, type)` lets suppliers depend on unapplied edits without writing storage.
The bridge remains a Nocturna API for Minecraft 1.20.1; newer Sodium mods still require their own version port.

## Gameplay review follow-up

An additional real-client pass adds ordinary Minecraft buttons and text fields to the RSO screen, as GUI addons
do. This reproduced a `ClassCastException` when a vanilla button received focus. Screen-level focus paths now
retain the native listener type; event dispatch supports both native and ported widgets. Native edit boxes
also activate the text-input session and supply their current caret position, so committed SDL text reaches
them through Minecraft/Forge rather than only through a direct test call.

The same repeated-open/disable sequence exposed a `minecraft:cloud_range` identifier collision between an
untyped native token and a typed legacy Extra option. The bridge reuses canonical identifiers when one side
is untyped; incompatible concrete types still fail. Tests cover both bridge directions and a genuine conflict.

Native input startup errors now leave GLFW keyboard input available for that settings session. A real-client
fault-injection check verifies that an unavailable SDL library does not make the settings screen unusable.
The validation profile snapshots/restores its RSO JSON so a failed disable/re-enable test cannot contaminate
the next run.

The broader review also reproduced an empty fence-queue read when a manually edited CPU render-ahead limit
was negative. That limit is treated as zero while retaining live limit reevaluation and normal fence waits.

To run the native-window input harness with a Java 17 JDK after preparing the SDL resources:

```text
node scripts/run-rso-native-input.mjs <JDK17-home> run modern/versions/1.20.1-forge/build/generated/rsoSdlResources
```

Prepare those resources with `:modern:1.20.1-forge:rsoSdlResources` using the Java 25 build toolchain.
Linux requires X11/XWayland, matching the official GLFW backend used by Minecraft 1.20.1.
The manual `RSO native input` workflow contains Linux and macOS runner jobs; it has not been dispatched.

To reproduce the source audit:

```text
node scripts/verify-rso-base.mjs <checkout-of-mc26.3-2.2.5>
```

For the search comparison, use `scripts/rso-reference-classpath.init.gradle` with each project's Gradle wrapper.
Run `:common:rsoWriteClasspath` in the reference checkout with `-Prso.enableControlify=false`, and
`:modern:1.20.1-forge:rsoWriteClasspath` in Nocturna. Each writes `build/audits/rso-runtime-classpath.txt`.
Then run:

```text
node scripts/verify-rso-search.mjs <RSO-checkout> <JDK17-home> <JDK25-home> <fastutil.jar> <Nocturna-rso-unicode.jar> <reference-classpath.txt> <Nocturna-classpath.txt>
```

RSO's MIT license and [ICU's release license notices](https://github.com/unicode-org/icu/blob/release-76-1/LICENSE)
are bundled as `LICENSE-RSO.txt` and `LICENSE-ICU.txt`. Nocturna remains LGPL-3.0.
