$ErrorActionPreference = "Stop"

$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) {
  Write-Host "Installing Ollama..."
  winget install --id Ollama.Ollama -e `
    --accept-package-agreements --accept-source-agreements --silent
  if ($LASTEXITCODE -ne 0) {
    throw "Ollama installation failed with exit code $LASTEXITCODE"
  }
}

$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) {
  $ollamaPath = Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe"
  if (Test-Path -LiteralPath $ollamaPath) {
    $ollama = Get-Item -LiteralPath $ollamaPath
  } else {
    throw "Ollama was installed but ollama.exe was not found. Reopen PowerShell and retry."
  }
}

Write-Host "Downloading qwen2.5vl:3b..."
& $ollama.Source pull qwen2.5vl:3b
if ($LASTEXITCODE -ne 0) {
  throw "Model download failed with exit code $LASTEXITCODE"
}

Write-Host "Vision model is ready: qwen2.5vl:3b"
