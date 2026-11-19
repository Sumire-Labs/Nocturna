param(
    [Parameter(Mandatory)][string]$Runtime,
    [Parameter(Mandatory)][string]$License,
    [Parameter(Mandatory)][string]$ResultDirectory,
    [string]$Installer = ''
)
$ErrorActionPreference = 'Stop'
$fixtureRuntime = (Resolve-Path -LiteralPath $Runtime).Path
$fixtureLicense = (Resolve-Path -LiteralPath $License).Path
if (!$Installer) { $Installer = Join-Path $PSScriptRoot 'Install-FsrRuntime.ps1' }
$artifactRoot = [IO.Path]::GetFullPath($ResultDirectory)
$testRoot = Join-Path $artifactRoot ([Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$script:requests = [Collections.Generic.List[string]]::new()
$script:licenseMode = 'valid'
$script:runtimeMode = 'valid'
$script:rejectSignature = $false
$cases = [Collections.Generic.List[string]]::new()
function Require([bool]$Condition, [string]$Message) { if (!$Condition) { throw $Message } }
function Invoke-WebRequest {
    param([string]$Uri, [string]$OutFile)
    $requests.Add($Uri)
    if ($Uri.EndsWith('/docs/license.md')) {
        if ($licenseMode -eq 'fail') { throw 'Fixture license network failure' }
        if ($licenseMode -eq 'corrupt') { [IO.File]::WriteAllText($OutFile, 'invalid license'); return }
        Copy-Item -LiteralPath $fixtureLicense -Destination $OutFile
    } elseif ($Uri.EndsWith('/amd_fidelityfx_upscaler_dx12.dll')) {
        if ($runtimeMode -eq 'corrupt') { [IO.File]::WriteAllBytes($OutFile, [byte[]]@(1,2,3)); return }
        Copy-Item -LiteralPath $fixtureRuntime -Destination $OutFile
    } else { throw "Unexpected fixture URL: $Uri" }
}
function Get-AuthenticodeSignature {
    param([string]$LiteralPath)
    if ($rejectSignature) { return [pscustomobject]@{Status='NotTrusted';SignerCertificate=$null} }
    Microsoft.PowerShell.Security\Get-AuthenticodeSignature -LiteralPath $LiteralPath
}
function New-Case([string]$Name) {
    $requests.Clear()
    $root = Join-Path $testRoot $Name
    New-Item -ItemType Directory -Force -Path (Join-Path $root 'config/nocturna/fsr') | Out-Null
    return $root
}
function Expect-Failure([string]$Root) {
    $failed = $false
    try { & $installer -GameDirectory $Root | Out-Null } catch { $failed = $true }
    Require $failed 'Installer accepted an invalid dependency'
    $dir = Join-Path $Root 'config/nocturna/fsr'
    Require (!(Test-Path -LiteralPath (Join-Path $dir 'amd_fidelityfx_upscaler_dx12.dll'))) 'Published a runtime after failed validation'
    Require (@(Get-ChildItem -LiteralPath $dir -Filter '*.download').Count -eq 0) 'Failed install left temporary files'
}

$repair = New-Case 'repair-license'
$repairDir = Join-Path $repair 'config/nocturna/fsr'
Copy-Item -LiteralPath $fixtureRuntime -Destination (Join-Path $repairDir 'amd_fidelityfx_upscaler_dx12.dll')
& $installer -GameDirectory $repair | Out-Null
Require ($requests.Count -eq 1 -and $requests[0].EndsWith('/docs/license.md')) 'Missing license was not repaired independently'
Require ((Get-FileHash -LiteralPath (Join-Path $repairDir 'AMD-FSR-LICENSE.md')).Hash -eq (Get-FileHash -LiteralPath $fixtureLicense).Hash) 'Repaired license differs'
$cases.Add('existing DLL / missing license repaired')
[IO.File]::WriteAllText((Join-Path $repairDir 'AMD-FSR-LICENSE.md'), 'corrupt')
$requests.Clear()
& $installer -GameDirectory $repair | Out-Null
Require ($requests.Count -eq 1) 'Modified license was not repaired'
$cases.Add('existing DLL / modified license repaired')
$requests.Clear(); $script:licenseMode = 'fail'
& $installer -GameDirectory $repair | Out-Null
Require ($requests.Count -eq 0) 'Complete install unexpectedly accessed the network'
$cases.Add('complete install idempotent offline')
$empty = New-Case 'license-network-failure'
Expect-Failure $empty
Require ($requests.Count -eq 1 -and $requests[0].EndsWith('/docs/license.md')) 'Fetched runtime despite unavailable license'
$cases.Add('license download failure publishes no DLL')
$script:licenseMode = 'corrupt'
Expect-Failure (New-Case 'license-hash-failure')
$cases.Add('wrong license hash rejected')
$script:licenseMode = 'valid'; $script:runtimeMode = 'corrupt'
Expect-Failure (New-Case 'runtime-hash-failure')
$cases.Add('wrong runtime hash rejected')
$script:runtimeMode = 'valid'; $script:rejectSignature = $true
Expect-Failure (New-Case 'runtime-signature-failure')
$cases.Add('untrusted runtime signature rejected')
$script:rejectSignature = $false
$fresh = New-Case 'fresh-install'
& $installer -GameDirectory $fresh | Out-Null
Require ($requests.Count -eq 2) 'Fresh install did not fetch both artifacts'
Require (Test-Path -LiteralPath (Join-Path $fresh 'config/nocturna/fsr/AMD-FSR-LICENSE.md')) 'Fresh install is missing its license'
$cases.Add('fresh signed install complete')
@{success=$true;cases=$cases.ToArray();network='fixture only';actualRuntimeSignatureVerified=$true} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $artifactRoot 'installer-results.json')
Write-Output "PASS $($cases.Count) installer transaction, repair, integrity and offline cases"
