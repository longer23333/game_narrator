$ErrorActionPreference = "Stop"
$toolDir = Join-Path $PSScriptRoot "..\tools\yt-dlp"
$exePath = Join-Path $toolDir "yt-dlp.exe"
$sumPath = Join-Path $toolDir "SHA2-256SUMS"
New-Item -ItemType Directory -Path $toolDir -Force | Out-Null

Invoke-WebRequest -UseBasicParsing `
  -Uri "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe" `
  -OutFile $exePath
Invoke-WebRequest -UseBasicParsing `
  -Uri "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS" `
  -OutFile $sumPath

$expectedLine = Get-Content -LiteralPath $sumPath |
  Where-Object { $_ -match "\syt-dlp\.exe$" } |
  Select-Object -First 1
if (-not $expectedLine) { throw "Official checksum for yt-dlp.exe was not found." }
$expected = ($expectedLine -split "\s+")[0].ToLowerInvariant()
$actual = (Get-FileHash -LiteralPath $exePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) {
  Remove-Item -LiteralPath $exePath -Force
  throw "yt-dlp SHA-256 verification failed."
}
Write-Host "Media importer ready: $exePath"
