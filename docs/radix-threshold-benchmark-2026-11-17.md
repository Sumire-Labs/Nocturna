Cutoffs 64 and 128 produced almost the same mean elapsed time in the balanced
synthetic workload. Cutoff 256 was slower overall. Ordered inputs favored
Merge sort, while random inputs and repeated distances favored earlier Radix
sort. These results do not justify changing Nocturna's current cutoff of 64.

## Results

Mean microseconds per distance sort, including distance-key calculation and
array allocation. Each row weights sizes 64, 96, 128, 192 and 255 equally.
Lower values are faster.

| Input order | Cutoff 64 | Cutoff 128 | Cutoff 256 |
| --- | ---: | ---: | ---: |
| Random positions | 1.511 | 1.720 | 5.394 |
| Nearly sorted | 1.383 | 1.188 | 1.082 |
| Already sorted | 1.836 | 1.465 | 0.716 |
| Eight repeated distance keys | 1.145 | 1.466 | 3.354 |
| Equal weight across all 20 workloads | 1.469 | 1.460 | 2.636 |

The 64/128 difference in that mixed mean is 0.6%, too small to select a new
default from this run. Cutoff 256 takes about 80% longer than 64 in the same
mixture. Average allocation is approximately 1,210, 1,344 and 1,814 bytes per
sort respectively. Radix reuses its work arrays; Merge allocates an additional
index-support array. Retained thread-local storage is separate from these
per-call allocation figures.

Longer confirmation runs on random positions gave:

| Quads | Cutoff 64 | Cutoff 128 | Cutoff 256 |
| --- | ---: | ---: | ---: |
| 64 | 0.741 | 0.563 | 0.668 |
| 96 | 0.858 | 2.000 | 2.002 |

The 64-element result moved from a near tie in the main run to a Merge-favored
result in confirmation. At 96 elements the earlier Radix path stayed faster.
The tradeoff depends on size and ordering; a single cutoff cannot win every
case. There is no captured distribution of actual Nocturna sort calls here,
so the balanced mean is not an estimate of time saved in a play session.

Controls at 32, 256 and 1,024 elements selected the same algorithm for all three
cutoffs. Their mean timings were close: approximately 0.155/0.158/0.156,
1.957/1.953/1.911 and 6.989/7.057/7.099 microseconds respectively. Allocation
profiles matched the expected paths in every case.

## Method

The machine was an AMD Ryzen AI MAX+ 395 with 16 cores and 32 logical processors
on Windows, using the Balanced power plan. JMH 1.37 ran on Temurin
17.0.20.1+1, with one worker, a fixed 512 MiB heap and G1 GC.

The launcher compiles the actual production RadixSort, MergeSort, InsertionSort,
AbstractSort and VertexSorters sources. It generates three benchmark-local
variants that differ only in class names and the cutoff comparison. Product
sources remain unchanged. The mapped Minecraft VertexSorting interface and
the cached JOML 1.10.5 jar provide the original API dependencies.

Each trial rotates through 128 seeded arrays. Trial setup compares every
variant's permutation with the production Merge sort before timing. JMH
consumes the returned index array. Distance calculation, the temporary key
array, sorting and the result array are inside the measured operation; input
generation and correctness checks are outside it.

The main run has 60 cases, three JVM forks, four 250 ms warmup iterations and
five 250 ms measurements per fork. Confirmation has six cases, two forks and
500 ms iterations. Controls have nine cases, two forks, two warmups and three
500 ms measurements. Raw data retains JMH confidence intervals and GC metrics.
Geometric ratios are also recorded for inspection; the mixed time above is
the arithmetic mean of equal-frequency calls.

```powershell
./scripts/run-radix-threshold-bench.ps1
node scripts/analyze-radix-threshold-bench.mjs
./scripts/run-radix-threshold-bench.ps1 -Forks 2 -IterationTime 500ms -Sizes '64,96' -Patterns random -ResultName confirm
./scripts/run-radix-threshold-bench.ps1 -Forks 2 -WarmupIterations 2 -MeasurementIterations 3 -IterationTime 500ms -Sizes '32,256,1024' -Patterns random -ResultName controls
```

The [measurement record](audits/radix-threshold-2026-11-17.json) includes source
hashes, runtime settings, individual results and references to compressed raw
JMH output. Unrelated shader work landed between runs; the measured sorting
source hashes stayed identical. This is a CPU microbenchmark of one distance
sorting path, with no FPS or frame-spike measurement. The production cutoff
remains 64.
