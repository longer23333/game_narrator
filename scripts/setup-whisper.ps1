param(
  [string]$ModelUrl = $(if ($env:WHISPER_MODEL_URL) { $env:WHISPER_MODEL_URL } else { "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin" })
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolDirectory = Join-Path $projectRoot "tools\whisper"
$modelDirectory = Join-Path $projectRoot "models"
$archivePath = Join-Path $toolDirectory "whisper-bin.zip"
$modelPath = Join-Path $modelDirectory "ggml-base.bin"

New-Item -ItemType Directory -Force -Path $toolDirectory, $modelDirectory | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $toolDirectory "Release\whisper-cli.exe"))) {
  Write-Host "Downloading whisper.cpp..."
  curl.exe -fL --retry 5 --retry-all-errors `
    -o $archivePath `
    "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.1/whisper-bin-x64.zip"
  if ($LASTEXITCODE -ne 0) {
    throw "whisper.cpp download failed with exit code $LASTEXITCODE"
  }
  Expand-Archive -LiteralPath $archivePath -DestinationPath $toolDirectory -Force
} else {
  Write-Host "whisper.cpp executable already exists; skipping binary download."
}

if (-not (Test-Path -LiteralPath $modelPath) -or (Get-Item -LiteralPath $modelPath).Length -lt 100MB) {
  Write-Host "Downloading multilingual base model..."
  $partialModelPath = "$modelPath.partial"
  curl.exe -fL --retry 5 --retry-all-errors -C - -o $partialModelPath $ModelUrl
  if ($LASTEXITCODE -ne 0) {
    throw "Whisper model download failed with exit code $LASTEXITCODE. Set WHISPER_MODEL_URL to an approved mirror and retry."
  }
  Move-Item -LiteralPath $partialModelPath -Destination $modelPath -Force
} else {
  Write-Host "Whisper model already exists; skipping model download."
}

$executable = Join-Path $toolDirectory "Release\whisper-cli.exe"
if (-not (Test-Path -LiteralPath $executable)) {
  throw "Installation incomplete: $executable was not created"
}

Write-Host "Whisper is ready."
Write-Host "Executable: $executable"
Write-Host "Model: $modelPath"
