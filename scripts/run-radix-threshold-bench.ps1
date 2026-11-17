param(
    [string]$JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot',
    [string]$CacheRoot = 'D:/DevCache/Gradle/celeritas-audit/caches/modules-2/files-2.1',
    [string]$Output = '.git/research/radix-threshold-bench',
    [int]$Forks = 3,
    [int]$WarmupIterations = 4,
    [int]$MeasurementIterations = 5,
    [string]$IterationTime = '250ms',
    [string]$Sizes = '64,96,128,192,255',
    [string]$Patterns = 'random,nearly_sorted,sorted,ties',
    [string]$ResultName = 'thresholds'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $outputRoot = [IO.Path]::GetFullPath($Output)
    New-Item -ItemType Directory -Force ($outputRoot + '/lib'), ($outputRoot + '/generated'), ($outputRoot + '/classes') | Out-Null
    $libraries = @(
        @('jmh-core-1.37.jar','org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar'),
        @('jmh-generator-annprocess-1.37.jar','org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar'),
        @('jopt-simple-5.0.4.jar','net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar'),
        @('commons-math3-3.6.1.jar','org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar')
    )
    foreach ($library in $libraries) {
        $destination = $outputRoot + '/lib/' + $library[0]
        if (-not (Test-Path -LiteralPath $destination)) { Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $library[1]) -OutFile $destination }
    }
    function CachedJar([string]$group, [string]$artifact, [string]$version) {
        $jar = Get-ChildItem -LiteralPath "$CacheRoot/$group/$artifact/$version" -Recurse -Filter "$artifact-$version.jar" | Select-Object -First 1
        if (-not $jar) { throw "Missing cached dependency: $group/$artifact/$version" }
        return $jar.FullName
    }
    $minecraft = [IO.Path]::GetFullPath('modern/versions/1.20.1-forge/build/moddev/artifacts/forge-1.20.1-47.3.0.jar')
    if (-not (Test-Path -LiteralPath $minecraft)) { throw 'Run the Forge build first to prepare its mapped Minecraft jar.' }
    $classpath = @((Get-ChildItem -LiteralPath ($outputRoot + '/lib') -Filter '*.jar').FullName)
    $classpath += CachedJar 'org.joml' 'joml' '1.10.5'
    $classpath += $minecraft
    $sourceRoot = 'common/src/main/java/org/embeddedt/embeddium/impl/util/sorting/'
    $radixPath = $sourceRoot + 'RadixSort.java'
    $sorterPath = 'modern/src/main/modern_java/org/embeddedt/embeddium/impl/modern/sorting/VertexSorters.java'
    $radix = [IO.File]::ReadAllText($radixPath)
    $sorter = [IO.File]::ReadAllText($sorterPath)
    if (-not $radix.Contains('if (n < 64)')) { throw 'Production cutoff changed; review the generation rule.' }
    $sources = @(
        ($sourceRoot+'AbstractSort.java')
        ($sourceRoot+'InsertionSort.java')
        ($sourceRoot+'MergeSort.java')
    )
    foreach ($cutoff in @(64,128,256)) {
        $radixVariant = [regex]::Replace($radix, '\bRadixSort\b', "RadixSort$cutoff").Replace('if (n < 64)', "if (n < $cutoff)")
        $sorterVariant = [regex]::Replace([regex]::Replace($sorter, '\bVertexSorters\b', "VertexSorters$cutoff"), '\bRadixSort\b', "RadixSort$cutoff")
        $radixTarget = "$outputRoot/generated/RadixSort$cutoff.java"
        $sorterTarget = "$outputRoot/generated/VertexSorters$cutoff.java"
        [IO.File]::WriteAllText($radixTarget, $radixVariant)
        [IO.File]::WriteAllText($sorterTarget, $sorterVariant)
        $sources += $radixTarget, $sorterTarget
    }
    $sources += 'scripts/fixtures/RadixThresholdBench.java'
    $cp = $classpath -join [IO.Path]::PathSeparator
    & "$JavaHome/bin/javac.exe" --release 17 -encoding UTF-8 -cp $cp -processorpath $cp -d ($outputRoot+'/classes') @sources
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark compilation failed' }
    $manifest = [ordered]@{
        checkedHead = (git rev-parse HEAD)
        java = (& "$JavaHome/bin/java.exe" -version 2>&1 | Out-String).Trim()
        cpu = (Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors)
        powerPlan = (powercfg /getactivescheme | Out-String).Trim()
        parameters = @{ forks=$Forks; warmups=$WarmupIterations; measurements=$MeasurementIterations; time=$IterationTime; sizes=$Sizes; patterns=$Patterns }
        sources = @($radixPath,$sorterPath,($sourceRoot+'AbstractSort.java'),($sourceRoot+'InsertionSort.java'),($sourceRoot+'MergeSort.java'),'scripts/fixtures/RadixThresholdBench.java') | ForEach-Object { @{path=$_;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $_).Hash.ToLowerInvariant()} }
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 "$outputRoot/$ResultName-environment.json"
    & "$JavaHome/bin/java.exe" -cp (($outputRoot+'/classes')+[IO.Path]::PathSeparator+$cp) org.openjdk.jmh.Main 'RadixThresholdBench.distanceSort' -bm avgt -tu ns -t 1 -f $Forks -wi $WarmupIterations -i $MeasurementIterations -w $IterationTime -r $IterationTime -p "size=$Sizes" -p "pattern=$Patterns" -prof gc -jvmArgs '-Xms512m -Xmx512m -XX:+UseG1GC' -foe true -rf json -rff "$outputRoot/$ResultName.json"
    if ($LASTEXITCODE -ne 0) { throw 'JMH failed' }
} finally { Pop-Location }
