param([string]$JavaHome = $env:JAVA_HOME, [string]$CMake = '', [switch]$UpdateManifest)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'include/jni.h'))) { throw 'Pass -JavaHome with a JDK installation' }
if (!$CMake) {
    $command = Get-Command cmake -ErrorAction SilentlyContinue
    if ($command) { $CMake = $command.Source }
    else {
        $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
        $installation = & $vswhere -latest -products '*' -property installationPath
        $CMake = Join-Path $installation 'Common7/IDE/CommonExtensions/Microsoft/CMake/CMake/bin/cmake.exe'
    }
}
$nativeSource = Join-Path $repository 'native/fsr'
$nativeBuild = Join-Path $nativeSource 'build'
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & $CMake -S $nativeSource -B $nativeBuild -A x64 -DBUILD_TESTING=ON
    if ($LASTEXITCODE -ne 0) { throw 'FSR bridge CMake configuration failed' }
    & $CMake --build $nativeBuild --config Release
    if ($LASTEXITCODE -ne 0) { throw 'FSR bridge build failed' }
    $ctest = Join-Path (Split-Path (Get-Command $CMake).Source -Parent) 'ctest.exe'
    & $ctest --test-dir $nativeBuild -C Release --output-on-failure
    if ($LASTEXITCODE -ne 0) { throw 'FSR fence protocol tests failed' }
} finally { $env:JAVA_HOME = $previousJavaHome }
$destination = Join-Path $repository 'modern/src/main/shaders_resources/native/windows-x64/nocturna_fsr.dll'
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
Copy-Item -LiteralPath (Join-Path $nativeBuild 'Release/nocturna_fsr.dll') -Destination $destination -Force
if ($UpdateManifest) { & node (Join-Path $repository 'scripts/verify-fsr-native.mjs') --write }
else { & node (Join-Path $repository 'scripts/verify-fsr-native.mjs') }
if ($LASTEXITCODE -ne 0) { throw 'FSR bridge manifest check failed' }
