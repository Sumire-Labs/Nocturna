# PBR resource indexing for Forge 1.20.1

Normal and specular map discovery now uses Angelica's asynchronous PBR resource index. Missing maps
can be rejected from a set of filenames instead of probing every ZIP and folder for each texture.
The index applies to both simple textures and atlas sprites. Java 17, mod IDs and public APIs stay unchanged.

## Source and behavior

The source is Angelica's LGPL-3.0 revision `996fb54cb05e256ea6039f4e2c8457a221a179b7`:

- [PBRResourceIndex.java](https://github.com/GTNewHorizons/Angelica/blob/996fb54cb05e256ea6039f4e2c8457a221a179b7/src/main/java/net/coderbot/iris/texture/pbr/PBRResourceIndex.java)
- [Original tests](https://github.com/GTNewHorizons/Angelica/blob/996fb54cb05e256ea6039f4e2c8457a221a179b7/src/test/java/net/coderbot/iris/texture/pbr/PBRResourceIndexTest.java)

The port retains the asynchronous scan, per-pack identity maps, cached positive and negative answers,
ZIP entry handling, folder case detection, traversal through links and fallback after scan failures.
It uses the existing worker pool ported from Angelica. Only `_n.png` and `_s.png` lookups are accelerated.
Other names and unknown resource managers always proceed to the existing lookup.

Known vanilla ZIP/folder packs and exact Forge path packs are indexed. Custom pack subclasses,
including Forge's mod-file resolver and delegating packs, use their actual `getResource` implementation.
This preserves custom resolution semantics. An unsupported pack remains usable alongside indexed packs.

The platform adapters account for differences from Minecraft 1.7.10:

- The texture manager starts the scan after texture reload and PBR holder clearing, at the same
  point as Angelica. A `ReloadableResourceManager` resolves to its current `MultiPackResourceManager`,
  so replacement invalidates cached answers. A lookup before this hook builds the index lazily.
- Pack entries are checked from highest priority to lowest. A pack's own resource takes precedence
  over its filter; filters can stop lower packs, including in namespaces provided only by a filter.
- Closing a stack waits for its pending scan before Minecraft closes the borrowed ZIP handles.
  The index never opens a second ZIP handle or closes a pack-owned handle.
- Folder entries include directories ending in a PBR suffix because the 1.20.1 path pack's lookup
  tests existence. Image decoding still determines whether that entry is a usable image.

Every positive answer proceeds through the existing loader. Minecraft still selects the resource and
metadata, and Celeritas still decodes, rescales, generates mipmaps and animates it. Its directional
texture exclusions and CIT path handling are retained.

## Verification

All 111 unit tests pass, including 20 PBR index tests. `PBRResourceIndexTest` covers Angelica's seven
original cases and the Forge adapters: pack priority,
multiple namespaces, custom resolvers, unknown managers, native filters, metadata selection, folder
case handling, non-texture paths, owner replacement, failed scans, ZIP ownership and close ordering.
The negative-query fixture issues 1,000 repeated requests without a ZIP probe and calls the unknown
pack resolver once for that location.

The source audit `node scripts/verify-angelica-pbr-index-port.mjs` compares the scan, answer cache
and failure fallback against the pinned original, with explicit platform adaptations.

`PBRResourceIndexValidation` runs the real loaders in a Java 17 Forge client with CCL,
Immersive Engineering and BrandonsCore loaded. It compares indexed and direct lookups using GPU
readback for simple normal/specular textures, texture metadata, animated atlas maps, interpolation
and mipmaps. It also checks missing maps, directional exclusions and actual reload/close hooks.
The atlas fixture exercises specular-map scaling and verifies that animation advances.

The final build and client validation also run in an isolated worktree containing only this change.
Commands and reports:

```text
gradlew.bat --gradle-user-home D:/DevCache/Gradle/celeritas-audit -PcompatRuntime build :modern:1.20.1-forge:runValidationClient --no-daemon
node scripts/verify-angelica-pbr-index-port.mjs <Angelica checkout>
node scripts/verify-celeritas-base.mjs <Celeritas checkout>
modern/versions/1.20.1-forge/run/pbr-resource-index-validation.txt
modern/versions/1.20.1-forge/run/migration-validation.txt
```

The build uses JDK 25 for Gradle; product compilation, tests and the game use Java 17. The product JAR
check rejects classes newer than Java 17 and excludes the validation mod and test addons.
The tests demonstrate fewer resource probes. Startup time and frame rate have not been benchmarked.
