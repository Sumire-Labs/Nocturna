# AMD's optional signed binary is downloaded separately; it is not included in the LGPL mod.
param([Parameter(Mandatory)][string]$GameDirectory)
$ErrorActionPreference = 'Stop'
$revision = '7539ae8b6568ad29ed78a753404351daf3b5cca7'
$expected = 'AAD4FCCCCB29084A655F9A4F9F613BD22C8C03FB4A0F8840148CC6543B4CCACE'
$directory = Join-Path ([IO.Path]::GetFullPath($GameDirectory)) 'config/nocturna/fsr'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$target = Join-Path $directory 'amd_fidelityfx_upscaler_dx12.dll'
if ((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $expected) {
    Write-Output "Verified existing AMD FSR runtime: $target"
    return
}
$temporary = Join-Path $directory ([Guid]::NewGuid().ToString() + '.download')
try {
    Invoke-WebRequest "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/$revision/Kits/FidelityFX/signedbin/amd_fidelityfx_upscaler_dx12.dll" -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $expected) { throw 'AMD runtime SHA-256 mismatch' }
    $signature = Get-AuthenticodeSignature -LiteralPath $temporary
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Advanced Micro Devices') {
        throw "Unexpected AMD runtime signature: $($signature.Status)"
    }
    Move-Item -LiteralPath $temporary -Destination $target -Force
    $licenseUrl = "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/$revision/docs/license.md"
    Invoke-WebRequest $licenseUrl -OutFile (Join-Path $directory 'AMD-FSR-LICENSE.md')
    Write-Output "Installed optional AMD FSR 4.1.2 runtime (also supplies FSR 2.3.4 / 3.1.6): $target"
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
