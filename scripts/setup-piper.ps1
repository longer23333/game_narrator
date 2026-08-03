$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot 'tools\piper'
$modelRoot = Join-Path $projectRoot 'models\piper'
$archive = Join-Path $toolRoot 'piper_windows_amd64.zip'

New-Item -ItemType Directory -Force -Path $toolRoot, $modelRoot | Out-Null

if (-not (Test-Path (Join-Path $toolRoot 'piper\piper.exe'))) {
    Write-Host 'Downloading Piper for Windows...'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip' -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $toolRoot -Force
}

$voice = Join-Path $modelRoot 'zh_CN-huayan-medium.onnx'
$voiceConfig = "$voice.json"
if (-not (Test-Path $voice)) {
    Write-Host 'Downloading Piper Chinese voice...'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://huggingface.co/rhasspy/piper-voices/resolve/main/zh/zh_CN/huayan/medium/zh_CN-huayan-medium.onnx?download=true' -OutFile $voice
}
if (-not (Test-Path $voiceConfig)) {
    Invoke-WebRequest -UseBasicParsing -Uri 'https://huggingface.co/rhasspy/piper-voices/resolve/main/zh/zh_CN/huayan/medium/zh_CN-huayan-medium.onnx.json?download=true' -OutFile $voiceConfig
}

Remove-Item $archive -ErrorAction SilentlyContinue
Write-Host "Piper is ready: $voice"
