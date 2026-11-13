param(
  [switch]$SkipBuild,
  [switch]$SkipDownloads,
  [switch]$FastCompression,
  [switch]$SkipFrontendRestore,
  [switch]$SkipSmoke,
  [switch]$FullInstallSmoke
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
# Windows env vars are case-insensitive; HTTPS_PROXY + https_proxy both set makes
# Start-Process throw "An item with the same key has already been added".
foreach ($proxyName in @('https_proxy', 'http_proxy', 'no_proxy', 'ftp_proxy', 'all_proxy')) {
  if (-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($proxyName)) -and
      -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($proxyName.ToUpperInvariant()))) {
    [Environment]::SetEnvironmentVariable($proxyName, $null, 'Process')
  }
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dist = Join-Path $projectRoot 'dist'
$staging = Join-Path $projectRoot 'release/staging'
$pom = [xml](Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw)
$appVersion = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($appVersion)) { throw 'Application version is missing from pom.xml' }

function Get-JavaVersionFromRuntime([string]$RuntimeRoot) {
  $releaseFile = Join-Path $RuntimeRoot 'release'
  if (Test-Path -LiteralPath $releaseFile) {
    $line = Get-Content -LiteralPath $releaseFile -ErrorAction SilentlyContinue |
      Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
    if ($line) { return (($line -replace '^JAVA_VERSION="?', '') -replace '"?\s*$', '') }
  }
  return 'unknown'
}

function Get-FreePort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

function Stop-Tree([int]$ProcessId) {
  try {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) { Stop-Tree ([int]$child.ProcessId) }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
  } catch { }
}

# 1) Build
if (-not $SkipBuild) {
  Write-Host '=== [1/4] Building Windows release ==='
  # Hashtable splatting binds switches by name (array splatting can pass them positionally).
  $buildParams = @{}
  if ($SkipDownloads) { $buildParams.SkipDownloads = $true }
  if ($FastCompression) { $buildParams.FastCompression = $true }
  if ($SkipFrontendRestore) { $buildParams.SkipFrontendRestore = $true }
  Write-Host ("Build switches: " + (($buildParams.Keys | Sort-Object) -join ',' ))
  & (Join-Path $PSScriptRoot 'build-windows-release.ps1') @buildParams
  if ($LASTEXITCODE -ne 0) { throw "Release build failed (exit $LASTEXITCODE)" }
} else {
  Write-Host '=== [1/4] Build skipped (-SkipBuild) ==='
}

$setup = Join-Path $dist "GameNarrator-Setup-$appVersion.exe"
if (-not (Test-Path -LiteralPath $setup -PathType Leaf)) { throw "Installer not found: $setup" }

# 2) SHA256 sidecar (sha256sum-compatible)
Write-Host '=== [2/4] Writing checksum ==='
$hash = (Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash.ToLowerInvariant()
$setupName = Split-Path -Leaf $setup
$checksumPath = "$setup.sha256"
Set-Content -LiteralPath $checksumPath -Encoding ascii -NoNewline -Value "$hash *$setupName`n"
Write-Host "SHA256 $hash"

# 3) Cold-start smoke on the staged artifact (what the installer ships)
$smokeStatus = 'SKIPPED'
$smokeDetail = ''
if (-not $SkipSmoke) {
  Write-Host '=== [3/4] Cold-start smoke (staged app) ==='
  $runtime = Join-Path $staging 'runtime/bin/java.exe'
  $jar = Join-Path $staging 'app/game-narrator.jar'
  if (-not (Test-Path -LiteralPath $runtime -PathType Leaf)) { throw "Staged runtime not found: $runtime" }
  if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Staged jar not found: $jar" }
  $smokeRoot = Join-Path $projectRoot 'target/publish-smoke'
  if (-not (Test-Path -LiteralPath $smokeRoot)) { New-Item -ItemType Directory -Path $smokeRoot -Force | Out-Null }
  $dataRoot = Join-Path $smokeRoot 'data'
  New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
  $port = Get-FreePort
  $stdout = Join-Path $smokeRoot 'stdout.log'
  $stderr = Join-Path $smokeRoot 'stderr.log'
  foreach ($f in @($stdout, $stderr)) { if (Test-Path -LiteralPath $f) { [IO.File]::Delete($f) } }
  $savedAppRoot = $env:GAME_NARRATOR_APP_ROOT
  $savedDataRoot = $env:GAME_NARRATOR_DATA_ROOT
  $savedOllama = $env:OLLAMA_BASE_URL
  $env:GAME_NARRATOR_APP_ROOT = $staging
  $env:GAME_NARRATOR_DATA_ROOT = $dataRoot
  # Never let smoke mutate a real Ollama install; point it at a closed port.
  $env:OLLAMA_BASE_URL = 'http://127.0.0.1:19999'
  $process = Start-Process -FilePath $runtime `
    -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', $jar, '--spring.profiles.active=release', "--server.port=$port") `
    -WorkingDirectory $staging -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
    -PassThru -WindowStyle Hidden
  $null = $process.Handle
  try {
    $health = $null
    for ($attempt = 0; $attempt -lt 150; $attempt++) {
      if ($process.HasExited) { break }
      try { $health = Invoke-RestMethod "http://127.0.0.1:$port/api/debug/health" -TimeoutSec 2; break }
      catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $health) {
      if (Test-Path $stdout) { Get-Content $stdout -Tail 60 }
      if (Test-Path $stderr) { Get-Content $stderr -Tail 60 }
      throw 'Cold-start smoke failed: backend health endpoint never became ready'
    }
    $missing = @()
    if (-not $health.ffmpegAvailable) { $missing += 'ffmpeg' }
    if (-not $health.whisperAvailable) { $missing += 'whisper' }
    if (-not $health.mediaImporterAvailable) { $missing += 'yt-dlp' }
    if ($missing.Count -gt 0) { throw "Cold-start smoke failed: missing bundled runtime ($($missing -join ', '))" }
    $smokeStatus = 'PASS'
    $smokeDetail = "health=OK port=$port"
    Write-Host "Smoke passed: $smokeDetail"
  } finally {
    if (-not $process.HasExited) { Stop-Tree $process.Id }
    $env:GAME_NARRATOR_APP_ROOT = $savedAppRoot
    $env:GAME_NARRATOR_DATA_ROOT = $savedDataRoot
    $env:OLLAMA_BASE_URL = $savedOllama
  }
}

# 4) Manifest + history
Write-Host '=== [4/4] Writing release manifest ==='
$commit = ''
try { $commit = (& git -C $projectRoot rev-parse --short HEAD 2>$null) } catch { $commit = '' }
$installer = Get-Item -LiteralPath $setup
$manifest = [ordered]@{
  version = $appVersion
  publishedAt = (Get-Date).ToString('o')
  commit = "$commit".Trim()
  installer = [ordered]@{
    name = $installer.Name
    bytes = $installer.Length
    sha256 = $hash
    checksumFile = (Split-Path -Leaf $checksumPath)
  }
  smoke = [ordered]@{
    staged = $smokeStatus
    detail = $smokeDetail
  }
  environment = [ordered]@{
    machine = $env:COMPUTERNAME
    # Bundled runtime version from the jlink image metadata (no process spawn, no stderr noise).
    java = (Get-JavaVersionFromRuntime (Join-Path $staging 'runtime'))
  }
}
$manifestPath = Join-Path $dist "GameNarrator-$appVersion.release.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$historyLine = ($manifest | ConvertTo-Json -Depth 6 -Compress)
Add-Content -LiteralPath (Join-Path $dist 'release-history.jsonl') -Encoding utf8 -Value $historyLine
Write-Host "Manifest: $manifestPath"

Write-Host ''
Write-Host '=== PUBLISH SUMMARY ==='
Write-Host ("version       : {0}" -f $appVersion)
Write-Host ("installer     : {0} ({1:N1} MB)" -f $installer.Name, ($installer.Length / 1MB))
Write-Host ("sha256        : {0}" -f $hash)
Write-Host ("cold-start    : {0}" -f $smokeStatus)
Write-Host ("manifest      : {0}" -f $manifestPath)

if ($FullInstallSmoke) {
  Write-Host '=== Optional: silent install + packaged runtime smoke ==='
  & (Join-Path $PSScriptRoot 'test-windows-clean-install.ps1') -BackendSmoke
  if ($LASTEXITCODE -ne 0) { throw "Full install smoke failed (exit $LASTEXITCODE)" }
  Write-Host 'Full install smoke passed.'
}
