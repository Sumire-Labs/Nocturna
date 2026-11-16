Sodium Extra 0.9.6-beta.4 is embedded in Nocturna's Forge 1.20.1 jar. Its
`me.flashyreese.mods.sodiumextra` package and configuration filename,
`sodium-extra-options.json`, are retained. Nocturna keeps its `embeddium` mod
id, existing packages and Java 17 runtime target.

## Sources

| Source | Pinned revision | Use |
| --- | --- | --- |
| [Sodium Extra](https://github.com/FlashyReese/sodium-extra/tree/7abb4236d5d6310633084cd2f613b63fdd1ccb5c) | `mc26.3-0.9.6-beta.4`, `7abb4236d5d6310633084cd2f613b63fdd1ccb5c` | Feature behavior, configuration, defaults, translations and shaders |
| [Rubidium/Embeddium Extra](https://github.com/dima-dencep/rubidium-extra/tree/697fc1fd29ecbd156bc7725a3f54b1b6fc28b3c6) | `1.20.1/stable`, `697fc1fd29ecbd156bc7725a3f54b1b6fc28b3c6` | Forge and older Minecraft integration reference |
| [Embeddium Extra for 1.21](https://github.com/dima-dencep/rubidium-extra/tree/ab30cc1850adab5b3dc56c1e1d8d4f0b9e12e365) | `1.21/stable`, `ab30cc1850adab5b3dc56c1e1d8d4f0b9e12e365` | Options-construction event reference |
| [Greenlight](https://github.com/FlashyReese/greenlight/tree/1d744b37d639433890521bde3a787bd9ddb2ae90) | `0.1.0`, `1d744b37d639433890521bde3a787bd9ddb2ae90` | Trusted server-pack policies and revocation |

The Extra and Greenlight sources are LGPL-3.0. License copies and attribution
ship in the jar. The integration does not import the newer Sodium renderer or
its configuration implementation.

## Layout and API adaptation

`modern/src/main/extra_java` holds Extra and the Forge adapters.
`modern/src/main/extra_resources` holds its assets, services, mixin configuration
and licenses. Both source roots belong to Nocturna's main mod; there is no
separate Extra mod container or required companion jar.

The original declarative options definitions build Nocturna's legacy public
option pages through `org.embeddedt.embeddium.impl.extra.compat.config`.
Pending values drive dependent controls before Apply. Bindings, defaults,
ranges, flags, particle ordering and dimension grouping follow the pinned
definitions. Named groups have noninteractive headings in the older layout.
The embedded RSO frontend receives typed controls, factory defaults and group
titles directly. Pending RSO edits drive the same dependency providers, including
when a control is reset to its default value.
VSync replaces the existing control in place. The newer Sodium-only spherical
render-fog option has no counterpart in this renderer.

Extra's CaffeineConfig copy uses an internal vendor package because another
dependency already exports its original package through Forge's module layer.
Its option names, properties file and mod override key are retained.

## Renderer behavior

Animation filtering occurs around atlas ticker calls, before Celeritas advances
an invisible animation. The complete upstream texture-category lists are
retained. Filtering does not change unrelated ticker implementations or move
the hook into Iris' separate PBR atlas loop.

Fog settings retain global and dimension overrides, inheritance on first edit,
protected gameplay rules, shape sentinels and the expanded cylindrical cull.
The Celeritas region shortcut and graph traversal use the same horizontal and
vertical limits as the terrain shader. Radial and planar modes retain the
upstream full-view cull. Forge fog listeners receive the adjusted distances and
can override them. Minecraft 1.20.1's cloud fade limit supplies the native cloud
range used by the percentage control.

Protected gameplay fog honors private-world permissions and Greenlight grants.
Only selected packs marked as server packs supply policies. Connection identity
and reload generation distinguish successive 1.20.1 server packs, which reuse
the same pack id. Local resource packs do not grant permission.

Panini uses the original projection, adaptive 4x4 color filter, outline-mask
restoration and nearest-surface depth shaders. The backend maps their passes to
1.20.1 RenderTargets. World and Fabulous targets are reused at the selected
quality; 100% quality avoids an unnecessary supersampling target. The raw glow
mask is projected before the native Sobel/blur chain. World depth and hand depth
remain separate until integration for later post effects. The hand and GUI use
the native projection. Shader packs, spyglass use, panorama rendering, captured
frusta, invalid projections and minimized windows preserve the upstream bypass
conditions. Allocation failures retry after a quality or window-size change.

HUD statistics retain the original five-second sample window and half-second
updates. HUD text refreshes at the same frame-loop boundary. Debug entries
provide Never, With debug overlay and Always modes, including the default
light-update warning. The steady-debug option caches the resulting text at the
configured tick interval. Toast filtering handles new, queued and visible toasts.

The remaining native hooks cover particles, rain splashes, block particles,
sky/sun/moon/stars, biome and fog colors, cloud height, entities and name tags,
beacons, pistons, enchanting books, light updates, instant sneak, spectator post
effects, adaptive VSync and the macOS window-density hint.

## Verification

`docs/audits/sodium-extra-port.json` maps all 78 upstream common Java files to
their port locations. Three unreferenced vertex declarations have no callers
in the pinned tree and are not compiled. The source verifier compares the full
options DSL, retained algorithms, animation categories and all 38 assets.

The test suite covers configuration recovery, serialized aliases, dimension
inheritance, particle filtering, shape sentinels, cylindrical limits and the
original Greenlight policy tests. The dedicated client checks actual controls,
pending dependencies, animation clocks and OpenGL projection output. Its color,
mask and depth checks compare GPU output with an independent CPU oracle at
strengths 0, 25 and 100.

The final workspace passed 157 unit tests, the full build and the mixin target
audit. The product check verified 4,447 classes at Java 17 bytecode
level or below. The client exercised 170 Extra controls, both frontends, RSO
default reset, real Fabulous targets, post-effect world/hand depth integration,
spectator-effect prevention and automatic Panini bypass with an active Iris
shader pack. It also checked native particles, biome tints, instant sneak,
queued toasts and the light-update warning.

Reproduce the checks from the repository root:

```powershell
node scripts/verify-sodium-extra-ports.mjs <pinned-sodium-extra-checkout>
./gradlew.bat :common:test :modern:1.20.1-forge:build
./gradlew.bat :modern:1.20.1-forge:runAuditClient
./gradlew.bat :modern:1.20.1-forge:runExtraValidationClient
```

The runtime checks use Java 17 on Windows with an AMD Radeon 8060S and OpenGL
4.6. Linux/EGL and macOS window-density behavior require testing on those
platforms. The compatibility checks do not establish a performance percentage
or guarantee every third-party shader pack.

Use Nocturna's jar by itself for Extra functionality. A standalone Extra jar
contains overlapping packages and should be removed when using this integration.
