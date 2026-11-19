param(
    [Parameter(Mandatory)][string]$ShaderPackDir,
    [Parameter(Mandatory)][string]$ResultDir,
    [string]$Checkout = '.',
    [string]$GradleUserHome = '',
    [string]$Label = 'candidate',
    [string[]]$Only = @(),
    [switch]$DisableCache,
    [switch]$Quick,
    [switch]$Elevated,
    [switch]$DumpShaders,
    [string]$UpscaleMode = 'FSR',
    [string]$RenderScale = 'NATIVE',
    [ValidateSet('', 'active', 'fallback')][string]$ExpectTemporal = ''
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repository = Split-Path $PSScriptRoot -Parent
$checkoutPath = (Resolve-Path -LiteralPath $Checkout).Path
$packDirectory = (Resolve-Path -LiteralPath $ShaderPackDir).Path
$results = [System.IO.Path]::GetFullPath($ResultDir)
New-Item -ItemType Directory -Force -Path $results | Out-Null
$manifest = Get-Content -LiteralPath "$repository/docs/audits/shader-pack-versions.json" -Raw | ConvertFrom-Json
$failures = 0
foreach ($pack in $manifest) {
    if ($Only.Count -gt 0 -and $pack.slug -notin $Only) { continue }
    $packPath = Join-Path $packDirectory $pack.filename
    $hash = (Get-FileHash -LiteralPath $packPath -Algorithm SHA512).Hash
    if ($hash -ine $pack.sha512) { throw "SHA-512 mismatch: $packPath" }
    $resultPath = Join-Path $results "$Label-$($pack.slug).json"
    $logPath = Join-Path $results "$Label-$($pack.slug).log"
    Write-Output "Checking $Label $($pack.slug)"
    $arguments = @(
        '--project-dir', $checkoutPath,
        '--init-script', "$repository/scripts/shader-probe.init.gradle",
        "-PshaderProbeSources=$repository/modern/src/shaderProbe",
        "-PshaderProbePack=$packPath",
        "-PshaderProbeResult=$resultPath",
        "-PshaderProbeLabel=$Label",
        "-PshaderProbeCache=$((!$DisableCache).ToString().ToLowerInvariant())",
        "-PshaderProbeQuick=$($Quick.IsPresent.ToString().ToLowerInvariant())",
        "-PshaderProbeElevated=$($Elevated.IsPresent.ToString().ToLowerInvariant())",
        "-PshaderProbeDebug=$($DumpShaders.IsPresent.ToString().ToLowerInvariant())",
        "-PshaderProbeUpscaleMode=$UpscaleMode",
        "-PshaderProbeRenderScale=$RenderScale",
        "-PshaderProbeExpectTemporal=$ExpectTemporal",
        ':modern:1.20.1-forge:runShaderProbeClient', '--no-daemon'
    )
    if ($GradleUserHome) { $arguments += @('--gradle-user-home', $GradleUserHome) }
    $started = [DateTime]::UtcNow
    & "$checkoutPath/gradlew.bat" @arguments *> $logPath
    $exitCode = $LASTEXITCODE
    if ((Test-Path -LiteralPath $resultPath) -and (Get-Item -LiteralPath $resultPath).LastWriteTimeUtc -ge $started) {
        $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if (!$result.success -or $exitCode -ne 0) { $failures++ }
        $result | Select-Object pack, success, error, warmStageCompiles, sourceReloadStageCompiles
    } else {
        $failures++
        Write-Warning "No result (Gradle exit $exitCode): $logPath"
    }
}
if ($failures -gt 0) { throw "$failures shader-pack probes failed; see JSON and logs in $results" }
