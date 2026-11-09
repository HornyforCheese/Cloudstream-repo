# The official Gradle wrapper is bundled. Restore it only if missing.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$destination = Join-Path $PSScriptRoot 'gradle\wrapper\gradle-wrapper.jar'
$expected = 'b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13'
if (-not (Test-Path $destination)) {
    Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar' -OutFile $destination -UseBasicParsing
}
if ((Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Gradle wrapper checksum does not match the official 9.3.1 release.'
}
Write-Host 'Checking and building (requires Android SDK 36 and a full JDK 17+)...'
& .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle checks failed. Check the error above.' }
Write-Host 'APK: app\build\outputs\apk\debug\app-debug.apk'
