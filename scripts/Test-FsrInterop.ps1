param(
    [Parameter(Mandatory)][string]$Runtime,
    [Parameter(Mandatory)][string]$JavaHome,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$ResultDirectory = '',
    [string]$Bridge = ''
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
if (!$GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
if (!$ResultDirectory) { $ResultDirectory = Join-Path $repository 'native/fsr/build/validation' }
New-Item -ItemType Directory -Force -Path $ResultDirectory | Out-Null
$ResultDirectory = [IO.Path]::GetFullPath($ResultDirectory)
if (!$Bridge) { $Bridge = Join-Path $repository 'modern/src/main/shaders_resources/native/windows-x64/nocturna_fsr.dll' }
$jars = foreach ($module in @('lwjgl','lwjgl-opengl','lwjgl-glfw')) {
    Get-ChildItem -LiteralPath (Join-Path $GradleUserHome "caches/modules-2/files-2.1/org.lwjgl/$module/3.3.1") -Recurse -Filter '*.jar' |
        Where-Object Name -Match "^$module-3\.3\.1(-natives-windows)?\.jar$"
}
if ($jars.Count -ne 6) { throw 'Expected the six Minecraft 1.20.1 LWJGL 3.3.1 Windows jars in the Gradle cache' }
$classpath = $jars.FullName -join ';'
$classes = Join-Path $ResultDirectory 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javaSources = foreach ($file in @('FsrNative.java','TemporalFsrUpscaler.java')) {
    Join-Path $repository "modern/src/main/shaders_java/net/irisshaders/iris/pathways/scaling/temporal/$file"
}
$fixtures = @('FsrNativeSmoke.java','FsrInteropSmoke.java','FsrMotionReference.java') | ForEach-Object { Join-Path $PSScriptRoot "fixtures/$_" }
& (Join-Path $JavaHome 'bin/javac.exe') --release 17 -cp $classpath -d $classes @javaSources @fixtures
if ($LASTEXITCODE -ne 0) { throw 'Java 17 FSR fixture compilation failed' }
foreach ($fixture in @('FsrNativeSmoke','FsrInteropSmoke','FsrMotionReference')) {
    & (Join-Path $JavaHome 'bin/java.exe') "-Dnocturna.fsr.bridge=$([IO.Path]::GetFullPath($Bridge))" "-Dnocturna.fsr.runtime=$([IO.Path]::GetFullPath($Runtime))" -cp "$classes;$classpath" $fixture 2>&1 |
        Tee-Object -FilePath (Join-Path $ResultDirectory "$fixture.log")
    if ($LASTEXITCODE -ne 0) { throw "$fixture failed" }
}
