# Sodium source provenance

The reviewed product sources do not import the PolyForm-only implementation from Sodium 0.5.12+, 0.6+
or the newer reference runtime used for RSO comparisons. Shared code inherited from the earlier LGPL Sodium
versions remains present. A class or package name containing `sodium` does not identify a newer import.

The renderer's pinned Celeritas base is `26f000239eb8ddf7ee191f0ddf369147e3c92ef6`.
Its README explicitly limits Sodium ancestry to 0.5.11 and earlier and excludes 0.5.12+/0.6+.
The sorting V3 introduction, `671ecaffc3da4ef91f68e0d2e001d3b509589ab7`, records an independent
implementation of the algorithms in Douira's thesis and explicitly states that neither non-FOSS Sodium PR
was used as a code source. Nocturna's Celeritas delta is checked by `scripts/verify-celeritas-base.mjs`.

The compatibility APIs come from the previous LGPL Embeddium base. Additional imports are the MIT RSO UI,
LGPL Angelica/Iris changes, privately packaged Unicode/ICU data and zlib-licensed SDL native libraries.
SDL's hidapi/yuv2rgb notices are retained; hidapi's BSD license option is used. See `UPSTREAM.md` and the
bundled license files for the source revisions and notices.

Sodium 0.9.2 binaries exist only in the research checkout/Gradle cache as a behavior oracle. Theme colors,
prompt behavior and validator results are recorded through public calls; their implementation bodies are
not copied into Nocturna. The reference UI pixel test uses MIT RSO sources and Nocturna's own LGPL adapter.
The product audit rejects the newer `net/caffeinemc/mods/sodium/client/` implementation namespace and checks
that the reference/validation code is absent. Dependency configuration embeds no Sodium implementation JAR.

This is a source/dependency/provenance review of the pinned imports and local changes, not a claim that every
line written by every upstream contributor has been independently re-derived.
