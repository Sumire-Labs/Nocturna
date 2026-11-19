# AMD's optional signed binary is downloaded separately; it is not included in the LGPL mod.
param([Parameter(Mandatory)][string]$GameDirectory)
$ErrorActionPreference = 'Stop'
$revision = '7539ae8b6568ad29ed78a753404351daf3b5cca7'
$expected = 'AAD4FCCCCB29084A655F9A4F9F613BD22C8C03FB4A0F8840148CC6543B4CCACE'
$expectedLicense = '410F9C138E766E846ACF2DC93DB768198DEF0293A017D7E3296815B451688DBE'
$directory = Join-Path ([IO.Path]::GetFullPath($GameDirectory)) 'config/nocturna/fsr'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$target = Join-Path $directory 'amd_fidelityfx_upscaler_dx12.dll'
$licenseTarget = Join-Path $directory 'AMD-FSR-LICENSE.md'
function Test-PinnedFile([string]$Path, [string]$Hash) {
    return (Test-Path -LiteralPath $Path -PathType Leaf) -and (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $Hash
}
$needRuntime = !(Test-PinnedFile $target $expected)
$needLicense = !(Test-PinnedFile $licenseTarget $expectedLicense)
if (!$needRuntime -and !$needLicense) {
    Write-Output "Verified existing AMD FSR runtime: $target"
    return
}
$temporary = Join-Path $directory ([Guid]::NewGuid().ToString() + '.download')
$temporaryLicense = Join-Path $directory ([Guid]::NewGuid().ToString() + '.license.download')
try {
    # Validate both artifacts before publishing the runtime. A retry also repairs a missing license.
    if ($needLicense) {
        Invoke-WebRequest "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/$revision/docs/license.md" -OutFile $temporaryLicense
        if (!(Test-PinnedFile $temporaryLicense $expectedLicense)) { throw 'AMD license SHA-256 mismatch' }
    }
    if ($needRuntime) {
        Invoke-WebRequest "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/$revision/Kits/FidelityFX/signedbin/amd_fidelityfx_upscaler_dx12.dll" -OutFile $temporary
        if (!(Test-PinnedFile $temporary $expected)) { throw 'AMD runtime SHA-256 mismatch' }
        $signature = Get-AuthenticodeSignature -LiteralPath $temporary
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Advanced Micro Devices') {
            throw "Unexpected AMD runtime signature: $($signature.Status)"
        }
    }
    if ($needLicense) { Move-Item -LiteralPath $temporaryLicense -Destination $licenseTarget -Force }
    if ($needRuntime) { Move-Item -LiteralPath $temporary -Destination $target -Force }
    Write-Output "Installed optional AMD FSR 4.1.2 runtime (also supplies FSR 2.3.4 / 3.1.6): $target"
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
    if (Test-Path -LiteralPath $temporaryLicense) { Remove-Item -LiteralPath $temporaryLicense }
}
