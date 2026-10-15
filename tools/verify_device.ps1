param([string]$DeviceSerial, [string]$JavaHome)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$scratchRoot = Join-Path $projectRoot ('.tools\device-tests\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $scratchRoot 'app') -Force | Out-Null

# A fresh workspace avoids stale source files; no existing app data or checkout is deleted.
foreach ($name in @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'local.properties')) {
    $source = Join-Path $projectRoot $name
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $scratchRoot }
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'gradle') -Destination $scratchRoot -Recurse
foreach ($name in @('src', 'schemas', 'proguard-rules.pro')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "app\$name") -Destination (Join-Path $scratchRoot 'app') -Recurse
}

$buildText = Get-Content (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
$identity = [regex]::Match($buildText, 'applicationId\s*=\s*"([^"]+)"')
if (-not $identity.Success) { throw 'Cannot find the application ID; refusing to run device tests.' }
$testIdentity = $identity.Groups[1].Value + '.verification'
$isolatedBuild = $buildText.Remove($identity.Index, $identity.Length).Insert($identity.Index, 'applicationId = "' + $testIdentity + '"')
[System.IO.File]::WriteAllText((Join-Path $scratchRoot 'app\build.gradle.kts'), $isolatedBuild)
# google-services.json and signing material are deliberately not copied.

$previousJava = $env:JAVA_HOME
$previousSerial = $env:ANDROID_SERIAL
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($DeviceSerial) { $env:ANDROID_SERIAL = $DeviceSerial }
    Write-Host "Testing isolated installation: $testIdentity"
    $wrapper = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { Join-Path $projectRoot 'gradlew.bat' } else { Join-Path $projectRoot 'gradlew' }
    & $wrapper -p $scratchRoot :app:connectedDebugAndroidTest --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Device tests failed. Reports: $scratchRoot\app\build\reports\androidTests\connected\debug\index.html" }
    Write-Host "Device tests passed. Report: $scratchRoot\app\build\reports\androidTests\connected\debug\index.html"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_SERIAL = $previousSerial
}
