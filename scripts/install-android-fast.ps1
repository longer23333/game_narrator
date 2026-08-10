param(
    [string]$Device = "",
    [string]$Apk = ""
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$adb = $null
$candidateAdb = Get-Command adb -ErrorAction SilentlyContinue
if ($candidateAdb) {
    $adb = $candidateAdb.Source
} else {
    foreach ($path in @((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
                        "G:\DevData\Android\Sdk\platform-tools\adb.exe")) {
        if (Test-Path $path) { $adb = $path; break }
    }
}
if (-not $adb) { throw "adb 未找到" }

$resolvedApk = $null
if ($Apk) {
    $resolvedApk = if ([IO.Path]::IsPathRooted($Apk)) { $Apk } else { Join-Path $projectRoot $Apk }
} else {
    $latest = Get-ChildItem (Join-Path $projectRoot "dist\GameNarrator-Android-*-release.apk") -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest) { $resolvedApk = $latest.FullName }
}
if (-not $resolvedApk -or -not (Test-Path $resolvedApk)) { throw "APK 不存在: $resolvedApk" }

$adbArgs = @()
if ($Device) { $adbArgs += "-s"; $adbArgs += $Device }
& $adb @adbArgs shell settings put global verifier_verify_adb_installs 0 | Out-Null
& $adb @adbArgs shell settings put global package_verifier_enable 0 | Out-Null
& $adb @adbArgs shell settings put secure install_non_market_apps 1 | Out-Null
& $adb @adbArgs install -r -g $resolvedApk
if ($LASTEXITCODE -ne 0) { throw "安装失败" }
Write-Host "安装完成: $resolvedApk"
