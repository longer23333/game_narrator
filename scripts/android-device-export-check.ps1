param(
    [string]$ApkPath = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $projectRoot "android-app\app\build\outputs\apk\debug\app-debug.apk"
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK not found: $ApkPath (run assembleDebug first)"
}

$adb = $null
$candidateAdb = Get-Command adb -ErrorAction SilentlyContinue
if ($candidateAdb) {
    $adb = $candidateAdb.Source
} elseif ($env:ANDROID_HOME) {
    $adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} elseif (Test-Path "G:\DevData\Android\Sdk\platform-tools\adb.exe") {
    $adb = "G:\DevData\Android\Sdk\platform-tools\adb.exe"
} elseif ($env:LOCALAPPDATA) {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}
if (-not $adb -or -not (Test-Path -LiteralPath $adb)) {
    throw "adb not found; connect a device or set ANDROID_HOME"
}

$deviceCount = (& $adb devices | Select-String "`tdevice$").Count
if ($deviceCount -lt 1) {
    Write-Host "NO_DEVICE: connect an Android device or start an emulator first"
    exit 2
}

Write-Host "Installing $ApkPath"
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw "adb install failed" }

& $adb logcat -c
$package = "cn.longer233.gamenarrator.mobile"
$activity = "$package/.MainActivity"
Write-Host "Launching $activity"
& $adb shell am start -n $activity
if ($LASTEXITCODE -ne 0) { throw "am start failed" }

Start-Sleep -Seconds 4
$pid = (& $adb shell pidof $package).Trim()
if ([string]::IsNullOrWhiteSpace($pid)) {
    Write-Host "FAIL: app process is not running"
    & $adb logcat -d -s AndroidRuntime:E
    exit 1
}

$fatal = & $adb logcat -d -s AndroidRuntime:E | Select-String "FATAL EXCEPTION"
if ($fatal) {
    Write-Host "FAIL: fatal exception detected"
    $fatal | Select-Object -First 20
    exit 1
}

Write-Host "PASS: app launched (pid=$pid) without fatal exception"
Write-Host "Next: manually import video, create project, edit timeline, export, and play the output on the device"
