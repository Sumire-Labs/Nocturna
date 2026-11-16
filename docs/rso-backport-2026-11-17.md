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
Their coordinates follow scrolling and layout changes. The legacy API does not expose defaults for arbitrary
custom options; these controls use RSO's original custom-control fallback, which has no reset/undo overlays.

When RSO is disabled, the classic Nocturna menu still exposes every RSO preference, including the enable switch.
Opening the former Nocturna screen class directly also selects RSO when enabled. Shader-pack selection continues
to open the bundled shader menu.

Minecraft 1.20.1 drawing, focus, input and narration APIs are adapted at the platform boundary.
Horizontal wheel input reaches RSO without losing Forge's surrounding input hooks. Windows IME composition
position follows the search caret. OS input behavior on Linux and macOS has not been exercised here.

The internal configuration bridge implements the contracts consumed by RSO. It does not expose the newer
Sodium configuration API to addons. No newer Sodium implementation sources are included.
The prompt and fingerprint compatibility helpers come from the previous LGPL Embeddium code.

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
| Common and Forge unit tests | 120 passed |
| Java 17 product class audit | 4,293 classes, no class version above 61 |
| RSO client with CCL, Immersive Engineering and BrandonsCore | Passed, 66 options registered |
| Renderer and addon integration client | Passed, including compact vertices, asynchronous terrain, legacy geometry hooks and weather |
| Full shader-pack warm reload | Zero stage compiles, including terrain programs |

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
