#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$BeforeNamedJar,
    [Parameter(Mandatory)][string]$AfterNamedJar,
    [Parameter(Mandatory)][string]$ShaderPack,
    [Parameter(Mandatory)][string]$ResultDir,
    [string]$BeforeRevision = 'before',
    [string]$AfterRevision = 'after',
    [string]$ControlNamedJar = '',
    [string]$ControlRevision = 'control',
    [string]$PreparedWorld = '',
    [string]$WorldMetadata = '',
    [string]$GradleUserHome = '',
    [string]$GradleJavaHome = '',
    [int]$Java = 17,
    [int]$Repetitions = 3,
    [int]$Warmup = 45,
    [int]$Seconds = 45,
    [Nullable[double]]$AnimationStart = $null,
    [switch]$FullHistory,
    [switch]$QueryProbe
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$fixture = Join-Path $PSScriptRoot 'fixtures/renderer-benchmark'
$output = [IO.Path]::GetFullPath($ResultDir)
if (Test-Path -LiteralPath $output) { throw "Use a new result directory: $output" }
if ($Repetitions -lt 1 -or $Warmup -lt 0 -or $Seconds -lt 1) { throw 'Invalid measurement durations or repetitions' }
if ($null -ne $AnimationStart -and (![double]::IsFinite($AnimationStart) -or $AnimationStart -lt 0 -or $AnimationStart -ge 3600)) { throw 'AnimationStart must be a finite value from 0 up to 3600 seconds' }
if ($QueryProbe -and $ControlNamedJar) { throw 'The query probe targets the post-port implementation; omit the control jar' }
$shader = (Resolve-Path -LiteralPath $ShaderPack).Path
$artifacts = [ordered]@{}
foreach ($entry in @(@('before',$BeforeNamedJar,$BeforeRevision),@('after',$AfterNamedJar,$AfterRevision),@('control',$ControlNamedJar,$ControlRevision))) {
    if (!$entry[1]) { continue }
    $jar = (Resolve-Path -LiteralPath $entry[1]).Path
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    $artifacts[$entry[0]] = @{ source=$jar; sha256=$hash; revision=$entry[2]; version=$hash.Substring(0,16); coordinate="benchmark.renderer:renderer:$($hash.Substring(0,16))" }
}
New-Item -ItemType Directory -Force -Path "$output/repo","$output/results","$output/runs" | Out-Null
foreach ($artifact in $artifacts.Values) {
    $dir = "$output/repo/benchmark/renderer/renderer/$($artifact.version)"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Copy-Item -LiteralPath $artifact.source -Destination "$dir/renderer-$($artifact.version).jar"
    "<project><modelVersion>4.0.0</modelVersion><groupId>benchmark.renderer</groupId><artifactId>renderer</artifactId><version>$($artifact.version)</version></project>" | Set-Content "$dir/renderer-$($artifact.version).pom"
}
$manifest = [ordered]@{ artifacts=$artifacts; shader=@{file=$shader;sha256=(Get-FileHash -LiteralPath $shader -Algorithm SHA256).Hash}; java=$Java; warmupSeconds=$Warmup; measureSeconds=$Seconds; repetitions=$Repetitions; fullHistory=[bool]$FullHistory; animationStart=$AnimationStart; probe=[bool]$QueryProbe; createdAt=(Get-Date -Format o) }
$manifest | ConvertTo-Json -Depth 7 | Set-Content "$output/manifest.json"
$oldJavaHome = $env:JAVA_HOME
if ($GradleJavaHome) { $env:JAVA_HOME = (Resolve-Path -LiteralPath $GradleJavaHome).Path }
function Invoke-RendererRun([string]$Variant,[string]$Name,[bool]$Prepare) {
    $otherGames = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object { $_.CommandLine -match 'devlaunch\.Main|net\.minecraft\.client\.main\.Main|net\.minecraft\.launchwrapper\.Launch|cpw\.mods\.bootstraplauncher\.BootstrapLauncher|org\.prismlauncher\.EntryPoint|org\.multimc\.EntryPoint|net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient' })
    if ($otherGames.Count) { throw 'Another Minecraft process is running; reserve the measurement window first' }
    $run = "$output/runs/$Name"
    New-Item -ItemType Directory -Force -Path "$run/config","$run/saves" | Out-Null
    if (!$Prepare) { Copy-Item -LiteralPath $script:world -Destination "$run/saves/benchmark" -Recurse }
    @{
        quality=@{weather_quality='FANCY';leaves_quality='FANCY';use_quad_normals_for_shading=$true;use_quad_normals_for_shading_v2=$true}
        advanced=@{cpu_render_ahead_limit=3;use_advanced_staging_buffers=$true}
        performance=@{chunk_builder_threads=8;always_defer_chunk_updates_v2=$true;animate_only_visible_textures=$true;use_entity_culling=$true;use_fog_occlusion=$true;use_block_face_culling=$true;use_compact_vertex_format=$false;use_translucent_face_sorting_v2=$true;use_no_error_g_l_context=$true}
        notifications=@{has_cleared_donation_button=$true;has_seen_donation_prompt=$true}
    } | ConvertTo-Json -Depth 6 | Set-Content "$run/config/embeddium-options.json"
    $artifact = $artifacts[$Variant]
    $result = if ($Prepare) { "$output/world-meta.json" } else { "$output/results/$Name.json" }
    $arguments = @('--project-dir',$fixture,"-PbenchRepository=$output/repo","-PrendererCoordinate=$($artifact.coordinate)","-PbenchRunDir=$run","-Pbench.label=$Name","-Pbench.result=$result","-PbenchJava=$Java","-Pbench.artifact=$($artifact.coordinate)","-Pbench.artifactSha256=$($artifact.sha256)","-Pbench.sourceCommit=$($artifact.revision)")
    if ($GradleUserHome) { $arguments += @('--gradle-user-home',$GradleUserHome) }
    if ($Prepare) { $arguments += '-Pbench.prepare=true' }
    else {
        $arguments += @("-Pbench.worldMeta=$script:metadata","-Pbench.shader=$shader","-Pbench.warmup=$Warmup","-Pbench.seconds=$Seconds")
        if (!$FullHistory) { $arguments += '-Pbench.shaderOnly=true' }
        if ($QueryProbe -and $Variant -eq 'after') { $arguments += '-Pbench.queryProbe=true' }
        if ($null -ne $AnimationStart) { $arguments += "-Pbench.animationStart=$($AnimationStart.ToString([Globalization.CultureInfo]::InvariantCulture))" }
    }
    $arguments += @('runBenchmark','--no-daemon')
    Write-Output "START $Name $(Get-Date -Format o)"
    & "$repository/gradlew.bat" @arguments *> "$output/results/$Name.log"
    if ($LASTEXITCODE -ne 0) { throw "$Name exited $LASTEXITCODE; see its log" }
    if (!$Prepare) {
        $r = Get-Content -LiteralPath $result -Raw | ConvertFrom-Json
        if (!$r.success) { throw "$Name failed: $($r.error)" }
        $r.results | Select-Object scenario,avgFps,p99Ms,maxMs,notificationQueriesPerFrame,updateQueriesPerFrame
    }
    Write-Output "DONE $Name $(Get-Date -Format o)"
}
try {
    if ($PreparedWorld) {
        if (!$WorldMetadata) { throw 'WorldMetadata is required with PreparedWorld' }
        $script:world = (Resolve-Path -LiteralPath $PreparedWorld).Path
        $script:metadata = (Resolve-Path -LiteralPath $WorldMetadata).Path
    } else {
        Invoke-RendererRun 'before' 'prepare' $true
        $script:world = "$output/runs/prepare/saves/benchmark"
        $script:metadata = "$output/world-meta.json"
    }
    $variants = @($artifacts.Keys)
    for ($round=0;$round -lt $Repetitions;$round++) {
        $order = if ($variants.Count -eq 2 -and $round % 2 -eq 1) { @('after','before') } else { @(0..($variants.Count-1) | ForEach-Object { $variants[($_+$round)%$variants.Count] }) }
        foreach ($variant in $order) { Invoke-RendererRun $variant "$variant-r$($round+1)-j$Java" $false }
    }
} finally { $env:JAVA_HOME = $oldJavaHome }
