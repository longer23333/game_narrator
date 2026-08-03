$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolDirectory = Join-Path $projectRoot "tools\whisper"
$modelDirectory = Join-Path $projectRoot "models"
$archivePath = Join-Path $toolDirectory "whisper-bin.zip"
$modelPath = Join-Path $modelDirectory "ggml-base.bin"

New-Item -ItemType Directory -Force -Path $toolDirectory, $modelDirectory | Out-Null

Write-Host "Downloading whisper.cpp..."
curl.exe -L --retry 5 --retry-all-errors `
  -o $archivePath `
  "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.1/whisper-bin-x64.zip"
if ($LASTEXITCODE -ne 0) {
  throw "whisper.cpp download failed with exit code $LASTEXITCODE"
}
Expand-Archive -LiteralPath $archivePath -DestinationPath $toolDirectory -Force

Write-Host "Downloading multilingual base model..."
curl.exe -L --retry 5 --retry-all-errors `
  -o $modelPath `
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
if ($LASTEXITCODE -ne 0) {
  throw "Whisper model download failed with exit code $LASTEXITCODE"
}

$executable = Join-Path $toolDirectory "Release\whisper-cli.exe"
if (-not (Test-Path -LiteralPath $executable)) {
  throw "Installation incomplete: $executable was not created"
}

Write-Host "Whisper is ready."
Write-Host "Executable: $executable"
Write-Host "Model: $modelPath"
