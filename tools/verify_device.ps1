param([string]$DeviceSerial, [string]$JavaHome, [string]$TestClass, [switch]$Release, [switch]$BuildOnly)

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
foreach ($name in @('src', 'schemas', 'proguard-rules.pro', 'proguard-test-rules.pro')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "app\$name") -Destination (Join-Path $scratchRoot 'app') -Recurse
}
$verificationManifest = Join-Path $scratchRoot 'app\src\main\AndroidManifest.xml'
$manifestText = [System.IO.File]::ReadAllText($verificationManifest).Replace('android:label="@string/app_name"', 'android:label="Agnihotra verification"')
[System.IO.File]::WriteAllText($verificationManifest, $manifestText)

$buildText = Get-Content (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
$identity = [regex]::Match($buildText, 'applicationId\s*=\s*"([^"]+)"')
if (-not $identity.Success) { throw 'Cannot find the application ID; refusing to run device tests.' }
$testIdentity = $identity.Groups[1].Value + '.verification'
$isolatedBuild = $buildText.Remove($identity.Index, $identity.Length).Insert($identity.Index, 'applicationId = "' + $testIdentity + '"')
if ($Release) {
    if (-not $TestClass) { $TestClass = 'com.nipunrautela.agnihotra.WidgetReleaseIntegrationTest' }
    # Exercise full R8/resource shrinking, signed with the disposable debug key only.
    $isolatedBuild += "`nandroid { testBuildType = `"release`"; buildTypes.getByName(`"release`") { signingConfig = signingConfigs.getByName(`"debug`") } }`n"
    # AndroidJUnitRunner shares this dependency with the target APK, but its
    # reflective startup is invisible to the app shrinker. Keep it in the
    # disposable target only; production widget/alarm code stays fully optimized.
    [System.IO.File]::AppendAllText((Join-Path $scratchRoot 'app\proguard-rules.pro'), "`n-keep class androidx.tracing.** { *; }`n-keep class kotlin.** { *; }`n")
}
[System.IO.File]::WriteAllText((Join-Path $scratchRoot 'app\build.gradle.kts'), $isolatedBuild)
# google-services.json and signing material are deliberately not copied.

$previousJava = $env:JAVA_HOME
$previousSerial = $env:ANDROID_SERIAL
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($DeviceSerial) { $env:ANDROID_SERIAL = $DeviceSerial }
    Write-Host "Testing isolated installation: $testIdentity"
    $wrapper = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { Join-Path $projectRoot 'gradlew.bat' } else { Join-Path $projectRoot 'gradlew' }
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    $tasks = if ($BuildOnly) { @(":app:assemble${variant}", ":app:assemble${variant}AndroidTest") } else { @(":app:connected${variant}AndroidTest") }
    $testArguments = @('-p', $scratchRoot) + $tasks + @('--no-daemon', '--console=plain')
    if ($TestClass) { $testArguments += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass" }
    & $wrapper @testArguments
    if ($LASTEXITCODE -ne 0) { throw "Device tests failed. Reports: $scratchRoot\app\build\reports\androidTests\connected" }
    if ($BuildOnly) { Write-Host "Verification APKs built (not installed/tested): $scratchRoot\app\build\outputs\apk" }
    else {
        $reports = Join-Path $scratchRoot 'app\build\outputs\androidTest-results\connected'
        $testCount = 0
        Get-ChildItem -LiteralPath $reports -Recurse -Filter 'TEST-*.xml' | ForEach-Object {
            [xml]$report = [System.IO.File]::ReadAllText($_.FullName)
            $testCount += $report.SelectNodes('//testcase').Count
        }
        if ($testCount -eq 0) { throw "No device tests were discovered; this is not a successful verification." }
        Write-Host "Device tests passed ($testCount tests). Reports: $scratchRoot\app\build\reports\androidTests\connected"
    }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_SERIAL = $previousSerial
}
