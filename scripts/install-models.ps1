param(
    [string]$Device = "",
    [switch]$Whisper,
    [switch]$Vision,
    [string]$VisionPath = "",
    [switch]$Text,
    [string]$TextPath = "",
    [string]$EnginePath = "",
    [switch]$Bundle
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$assetsModels = Join-Path $projectRoot "android-app\app\src\main\assets\models"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    $candidate = $null
    foreach ($path in @((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
                        "G:\DevData\Android\Sdk\platform-tools\adb.exe")) {
        if (Test-Path $path) { $candidate = $path; break }
    }
    if (-not $candidate) { throw "未找到 adb，请安装 Android SDK 平台工具" }
    $adb = $candidate
}
$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

$modelsDir = "/sdcard/Android/data/cn.longer233.gamenarrator.mobile/files/models"
$tmp = Join-Path $env:TEMP "gamenarrator-models"
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

function Push-Model([string]$local, [string]$name) {
    if (-not (Test-Path $local)) { throw "模型文件不存在: $local" }
    & $adb @adbArgs shell mkdir -p $modelsDir
    if ($LASTEXITCODE -ne 0) { throw "无法创建设备模型目录" }
    & $adb @adbArgs push $local "$modelsDir/$name"
    if ($LASTEXITCODE -ne 0) { throw "模型推送失败: $name" }
    Write-Host "已安装: $name"
}

function Copy-ToAssets([string]$local, [string]$name) {
    if (-not (Test-Path $local)) { throw "模型文件不存在: $local" }
    New-Item -ItemType Directory -Path $assetsModels -Force | Out-Null
    Copy-Item -LiteralPath $local -Destination (Join-Path $assetsModels $name) -Force
    Write-Host "已放入 assets/models: $name"
}

if ($Whisper) {
    $whisperFile = Join-Path $tmp "whisper-tiny.bin"
    if (-not (Test-Path $whisperFile)) {
        Write-Host "下载 Whisper tiny 模型（约 75 MB）…"
        Invoke-WebRequest -Uri "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" -OutFile $whisperFile
    }
    Push-Model $whisperFile "whisper-tiny.bin"
    if ($Bundle) { Copy-ToAssets $whisperFile "whisper-tiny.bin" }
}
if ($Vision) {
    $visionName = Split-Path -Leaf $VisionPath
    Push-Model $VisionPath $visionName
    if ($Bundle) { Copy-ToAssets $VisionPath $visionName }
}
if ($Text) {
    $textName = Split-Path -Leaf $TextPath
    Push-Model $TextPath $textName
    if ($Bundle) { Copy-ToAssets $TextPath $textName }
}
if ($EnginePath) {
    Push-Model $EnginePath "whisper-cli"
    if ($Bundle) { Copy-ToAssets $EnginePath "whisper-cli" }
}

if (-not $Whisper -and -not $Vision -and -not $Text -and -not $EnginePath) {
    Write-Host "用法：install-models.ps1 [-Device <serial>] [-Whisper] [-Vision -VisionPath <file>] [-Text -TextPath <file>] [-EnginePath <whisper-cli>] [-Bundle]"
    Write-Host "也可以把 whisper-*.bin / vision-*.onnx / text-*.onnx 放入 android-app/app/src/main/assets/models/ 后直接构建 APK，首次启动会自动复制。"
    Write-Host "Whisper 转写还需要 whisper-cli（whisper.cpp Android 可执行文件）与模型放在同一模型目录。"
} else {
    Write-Host "完成。设备模型目录: $modelsDir"
}
