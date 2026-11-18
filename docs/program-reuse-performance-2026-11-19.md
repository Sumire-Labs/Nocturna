# Linked-program reuse performance investigation

The controlled BSL A/B measurements did not reproduce a slowdown from the linked-program reuse port. The rendering implementation and its binding-safety checks remain unchanged.

## Snapshots and method

- Before: `9f682192d6bbc95e7542ef44993f24ab5754b572`.
- After: `310fca27b96696924dfaf5f0572a8382fc7115ff`.
- Celeritas control: `26f000239eb8ddf7ee191f0ddf369147e3c92ef6`.
- Ryzen AI MAX+ 395, Radeon 8060S, driver `32.0.32015.7029`.
- Forge 1.20.1-47.3.0 development runtime, matching named mappings, Temurin Java 21.0.12.1, 6 GiB heap, G1, VSync off, uncapped FPS.
- BSL 10.1.8, 1280 x 720, render distance 12, eight chunk workers, CPU render-ahead 3, non-compact vertices and quad-normal shading.
- Identical generated world, seed `20261117`, fixed view and daylight, no random ticks or mob spawning.
- Three fresh JVMs per variant, with the order rotated across blocks. Each shader-only run has a 45-second warmup and 45-second recording. Shader loading and screenshots are outside the recording interval.

The named shaded jars use the compiled renderer classes and production shadow dependencies. The benchmark does not replace renderer algorithms. These are development-runtime results on one machine, not a production modpack ranking.

## Saved A/B results

Measurements from 2026-11-17, preserved across the pause:

| Variant | Median FPS | Run range | Median p99, ms |
| --- | ---: | ---: | ---: |
| Before reuse | 458.79 | 443.68 to 470.16 | 3.9294 |
| After reuse | 466.79 | 423.32 to 471.83 | 3.3879 |
| Celeritas control | 470.63 | 467.91 to 473.22 | 3.2312 |

The after-port median is 1.7% higher than before, with overlapping run ranges. This does not establish a performance improvement. It provides no support for the earlier hypothesis of an approximately 18% loss caused by this port.

A separate instrumented BSL run counted zero `GL_CURRENT_PROGRAM` queries from uniform notification callbacks and six queries per frame from `ProgramUniforms.update`. Its FPS is excluded from the uninstrumented A/B results. The notification guard was therefore not an observed hot path in this scene. Other scenes can still use it, so removing the guard would not be a justified repair.

## Scene-history check

On 2026-11-19, the original sequence was replayed: terrain, 400 item entities, snowfall, travel through generated chunks, then BSL. Each scene used 15 seconds of warmup and 30 seconds of recording.

| Variant | BSL FPS | p99, ms |
| --- | ---: | ---: |
| Before reuse | 349.79 | 3.5818 |
| After reuse | 399.13 | 3.2746 |
| Celeritas control | 418.37 | 3.2274 |

These are single contextual checks, not three-run medians. The low BSL result also occurs before the reuse port. A difference between Nocturna and Celeritas in this protocol cannot be assigned to the reuse change.

The harness records shader time at the start and end of measurement. The native timer quantizes frame deltas to milliseconds, so matching wall-clock durations alone do not guarantee matching animation times. The optional `AnimationStart` control advances `frameTimeCounter` at a common wall-clock rate. This test-only mixin belongs to the benchmark fixture and is never included in Nocturna.

## Conditioning and animation inputs

Additional single-run checks used the post-port snapshot and Celeritas. They are kept separate from the three-run A/B medians:

| Preparation | Warmup / recording, seconds | Animation input | Nocturna FPS | Celeritas FPS |
| --- | --- | --- | ---: | ---: |
| Five-scene history | 15 / 30 | Native | 399.13 | 418.37 |
| Five-scene history | 15 / 30 | Common wall-clock rate | 366.98 | 417.15 |
| Shader-only | 15 / 30 | Native | 365.16 | 474.52 |
| Shader-only | 45 / 30 | Common wall-clock rate, same recorded phase | 456.33 | 468.35 |

The last check starts the test clock at 3570 seconds and uses the original hourly wrap, so its recorded animation interval is approximately 15 to 45 seconds. The shorter controlled run records that same interval. Keeping animation time comparable does not remove every Nocturna/Celeritas difference, and these protocols also differ in scene history. They show that the result depends on conditioning and input state; they do not isolate JIT compilation, driver warmup or temporal shader history as the single cause.

The earlier 335 versus 406 FPS comparison is therefore not evidence that linked-program reuse introduced an 18% regression. A residual difference in some short-conditioning protocols remains a separate question. No speculative removal of uniform initialization or unbound-notification protection is included.

## Java 17 and regression validation

A Java 17 shader-only screening pair, with 45 seconds of warmup and recording, produced 391.67 FPS before and 449.43 FPS after. One pair is a compatibility/performance screen, not proof of an improvement.

The current branch snapshot `34fbea7a` passed 187 unit tests and the Java 17 Forge GPU validation. Reload and shader OFF/ON each reused 61 programs, including six terrain programs and one compute program, with zero new links and zero binary restores. The GPU tests cover consuming ownership, two generations, configuration invalidation, pixels, initial zero and identity values, deferred notifications, sampler/image state, compute, terrain and failures.

All eight pinned real packs passed on Java 17: Complementary Reimagined 5.9.3, Complementary Unbound 5.9.3, BSL 10.1.8, Photon 1.4, Solas 3.9, MakeUp UltraFast 9.6, Rethinking Voxels 0.1-beta9 and Mellow 3.5. Each probe checks an active pipeline and OpenGL errors in dry weather, instanced rain, original rain, snow, warm reload and reload with binary saving disabled. Warm stage compilations were zero for every pack. These are scene-based regression checks, not exhaustive shader-specification conformance tests.

Photon also passed the dry/rain/snow probe with the fixture elevated by 124 blocks, covering the normal-height sky case separately from the flat-world fixture.

Machine-readable measurements and raw-frame hashes are in `audits/program-reuse-performance-2026-11-19.json`. The compressed raw evidence retains successful measurement JSON files and their shader frame CSVs. Interrupted runs and the instrumented query probe are excluded from the normal A/B medians.

## Reproduction

Build named shaded artifacts from isolated checkouts of the revisions above:

```powershell
./gradlew.bat --init-script <benchmark-script-dir>/shader-benchmark.init.gradle :modern:1.20.1-forge:benchNamedJar --no-daemon
```

Use the resulting `modern/versions/1.20.1-forge/build/benchmark/benchmark-named.jar` files, not SRG-remapped distribution jars, with the isolated fixture:

```powershell
pwsh ./scripts/Test-ProgramReusePerformance.ps1 -BeforeNamedJar <before-named.jar> -AfterNamedJar <after-named.jar> -BeforeRevision 9f682192 -AfterRevision 310fca27 -ControlNamedJar <celeritas-named.jar> -ControlRevision 26f00023 -ShaderPack <BSL_v10.1.8.zip> -ResultDir <new-output-directory> -Java 21 -Warmup 45 -Seconds 45 -Repetitions 3 -GradleJavaHome <jdk25-directory>
python ./scripts/analyze-program-reuse-benchmark.py <new-output-directory>/results --output <new-output-directory>/analysis.json
```

The runner records jar and shader SHA-256 hashes, runtime, settings, camera, rain strength, entity count, raw frame intervals, GC/safepoint logs and screenshots. It refuses an existing output directory or another Minecraft process. Reserve CPU/GPU time externally before starting; the script does not stop other applications. `PreparedWorld` with `WorldMetadata` can reuse a generated fixture, and `FullHistory` selects the five-scene sequence. `QueryProbe` is diagnostic only and must not be pooled with normal FPS runs.

The analyzer recalculates FPS and p99 from raw frame intervals. JVM version, warmup, recording length, scene history and animation control remain separate groups. No shader packs or renderer binaries are redistributed with this fixture.
