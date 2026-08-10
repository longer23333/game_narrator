param(
    [string]$Device = "",
    [string]$OutDir = "docs\screenshots"
)
$ErrorActionPreference = "Stop"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    $candidate = $null
    foreach ($path in @((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
                        "G:\DevData\Android\Sdk\platform-tools\adb.exe")) {
        if (Test-Path $path) { $candidate = $path; break }
    }
    if (-not $candidate) { throw "未找到 adb" }
    $adb = $candidate
}

$out = Join-Path $PSScriptRoot "..\$OutDir"
New-Item -ItemType Directory -Path $out -Force | Out-Null

function Invoke-Adb([string[]]$arguments) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $adb
    $psi.Arguments = ($arguments -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -and $stderr) { Write-Host $stderr }
    return $stdout
}

function Capture-AdbScreen([string]$name) {
    Start-Sleep -Seconds 2
    $args = @()
    if ($Device) { $args += "-s"; $args += $Device }
    $args += "exec-out"; $args += "screencap"; $args += "-p"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $adb
    $psi.Arguments = ($args -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    $stream = [System.IO.File]::Create((Join-Path $out "$name.png"))
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
    } finally {
        $stream.Dispose()
        $process.WaitForExit()
    }
    Write-Host "已截图: $name.png"
}

function Tap-Text([string]$text) {
    for ($i = 0; $i -lt 3; $i++) {
        $dumpArgs = @()
        if ($Device) { $dumpArgs += "-s"; $dumpArgs += $Device }
        $dumpArgs += "shell"; $dumpArgs += "uiautomator"; $dumpArgs += "dump"; $dumpArgs += "/sdcard/ui.xml"
        Invoke-Adb $dumpArgs | Out-Null
        $pullArgs = @()
        if ($Device) { $pullArgs += "-s"; $pullArgs += $Device }
        $pullArgs += "pull"; $pullArgs += "/sdcard/ui.xml"; $pullArgs += (Join-Path $env:TEMP "ui.xml")
        Invoke-Adb $pullArgs | Out-Null
        $xml = $null
        try { $xml = [System.IO.File]::ReadAllText((Join-Path $env:TEMP "ui.xml"), [System.Text.Encoding]::UTF8) } catch { }
        if ($xml) {
            $pattern = 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            $match = [regex]::Match($xml, $pattern)
            if ($match.Success) {
                $x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
                $y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
                $tapArgs = @()
                if ($Device) { $tapArgs += "-s"; $tapArgs += $Device }
                $tapArgs += "shell"; $tapArgs += "input"; $tapArgs += "tap"; $tapArgs += $x; $tapArgs += $y
                Invoke-Adb $tapArgs | Out-Null
                return
            }
        }
        Start-Sleep -Seconds 3
    }
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $dumpArgs = @()
        if ($Device) { $dumpArgs += "-s"; $dumpArgs += $Device }
        $dumpArgs += "shell"; $dumpArgs += "uiautomator"; $dumpArgs += "dump"; $dumpArgs += "/sdcard/ui.xml"
        Invoke-Adb $dumpArgs | Out-Null
        $pullArgs = @()
        if ($Device) { $pullArgs += "-s"; $pullArgs += $Device }
        $pullArgs += "pull"; $pullArgs += "/sdcard/ui.xml"; $pullArgs += (Join-Path $env:TEMP "ui.xml")
        Invoke-Adb $pullArgs | Out-Null
        $xml = $null
        try { $xml = [System.IO.File]::ReadAllText((Join-Path $env:TEMP "ui.xml"), [System.Text.Encoding]::UTF8) } catch { }
        $match = $null
        if ($xml) {
            $pattern = 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            $match = [regex]::Match($xml, $pattern)
        }
        if ($match -and $match.Success) {
            $x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
            $y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
            $tapArgs = @()
            if ($Device) { $tapArgs += "-s"; $tapArgs += $Device }
            $tapArgs += "shell"; $tapArgs += "input"; $tapArgs += "tap"; $tapArgs += $x; $tapArgs += $y
            Invoke-Adb $tapArgs | Out-Null
            return
        }
        $row = $null
        if ($xml) {
            $rowMatch = [regex]::Match($xml,
                    'class="android.widget.HorizontalScrollView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
            if ($rowMatch.Success) {
                $x1 = [int]$rowMatch.Groups[1].Value
                $y1 = [int]$rowMatch.Groups[2].Value
                $x2 = [int]$rowMatch.Groups[3].Value
                $y2 = [int]$rowMatch.Groups[4].Value
                $row = @{ X1 = $x1; X2 = $x2; Y = [int](($y1 + $y2) / 2) }
            }
        }
        $fromX = if ($row) { $row.X2 - 80 } else { 900 }
        $toX = if ($row) { $row.X1 + 80 } else { 100 }
        $swipeY = if ($row) { $row.Y } else { 650 }
        $swipeArgs = @()
        if ($Device) { $swipeArgs += "-s"; $swipeArgs += $Device }
        $swipeArgs += "shell"; $swipeArgs += "input"; $swipeArgs += "swipe"
        $swipeArgs += $fromX; $swipeArgs += $swipeY; $swipeArgs += $toX; $swipeArgs += $swipeY; $swipeArgs += "400"
        Invoke-Adb $swipeArgs | Out-Null
        Start-Sleep -Seconds 3
    }
    Write-Host "未找到导航按钮: $text"
}

$startArgs = @()
if ($Device) { $startArgs += "-s"; $startArgs += $Device }
$startArgs += "shell"; $startArgs += "am"; $startArgs += "force-stop"; $startArgs += "cn.longer233.gamenarrator.mobile"
Invoke-Adb $startArgs | Out-Null
$startArgs = @()
if ($Device) { $startArgs += "-s"; $startArgs += $Device }
$startArgs += "shell"; $startArgs += "am"; $startArgs += "start"; $startArgs += "-n"; $startArgs += "cn.longer233.gamenarrator.mobile/.MainActivity"
Invoke-Adb $startArgs | Out-Null
Start-Sleep -Seconds 6
Capture-AdbScreen "home"

foreach ($page in @("剪辑任务", "平台导入", "素材库", "设置")) {
    $stopArgs = @()
    if ($Device) { $stopArgs += "-s"; $stopArgs += $Device }
    $stopArgs += "shell"; $stopArgs += "am"; $stopArgs += "force-stop"; $stopArgs += "cn.longer233.gamenarrator.mobile"
    Invoke-Adb $stopArgs | Out-Null
    $relaunchArgs = @()
    if ($Device) { $relaunchArgs += "-s"; $relaunchArgs += $Device }
    $relaunchArgs += "shell"; $relaunchArgs += "am"; $relaunchArgs += "start"; $relaunchArgs += "-n"; $relaunchArgs += "cn.longer233.gamenarrator.mobile/.MainActivity"
    Invoke-Adb $relaunchArgs | Out-Null
    Start-Sleep -Seconds 6
    Tap-Text $page
    Capture-AdbScreen ($page -replace "[^\w]", "_")
}

foreach ($secondary in @("镜头搜索", "切片合集")) {
    $stopArgs = @()
    if ($Device) { $stopArgs += "-s"; $stopArgs += $Device }
    $stopArgs += "shell"; $stopArgs += "am"; $stopArgs += "force-stop"; $stopArgs += "cn.longer233.gamenarrator.mobile"
    Invoke-Adb $stopArgs | Out-Null
    $relaunchArgs = @()
    if ($Device) { $relaunchArgs += "-s"; $relaunchArgs += $Device }
    $relaunchArgs += "shell"; $relaunchArgs += "am"; $relaunchArgs += "start"; $relaunchArgs += "-n"; $relaunchArgs += "cn.longer233.gamenarrator.mobile/.MainActivity"
    Invoke-Adb $relaunchArgs | Out-Null
    Start-Sleep -Seconds 6
    Tap-Text $secondary
    Capture-AdbScreen ($secondary -replace "[^\w]", "_")
}

$stopArgs = @()
if ($Device) { $stopArgs += "-s"; $stopArgs += $Device }
$stopArgs += "shell"; $stopArgs += "am"; $stopArgs += "force-stop"; $stopArgs += "cn.longer233.gamenarrator.mobile"
Invoke-Adb $stopArgs | Out-Null
$relaunchArgs = @()
if ($Device) { $relaunchArgs += "-s"; $relaunchArgs += $Device }
$relaunchArgs += "shell"; $relaunchArgs += "am"; $relaunchArgs += "start"; $relaunchArgs += "-n"; $relaunchArgs += "cn.longer233.gamenarrator.mobile/.MainActivity"
Invoke-Adb $relaunchArgs | Out-Null
Start-Sleep -Seconds 6
Tap-Text "设置"
Start-Sleep -Seconds 2
Tap-Text "打开 AI 设置 · 用量 · 模型检查"
Capture-AdbScreen "AI_设置"

Write-Host "完成。截图目录: $out"
