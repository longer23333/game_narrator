$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$frontendRoot = Join-Path $projectRoot 'frontend'

function Invoke-Npm([string[]]$Arguments, [string]$failureMessage) {
  # Windows PowerShell promotes native stderr (including npm warnings) to ErrorRecord when redirected.
  $previousErrorAction = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $npm @Arguments 2>&1 | ForEach-Object { Write-Host $_ }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorAction
  }
  if ($exitCode -ne 0) { throw $failureMessage }
}

function Invoke-VerificationScript([string]$RelativePath, [string]$failureMessage) {
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $projectRoot $RelativePath)
  if ($LASTEXITCODE -ne 0) { throw $failureMessage }
}

function Test-WorkspaceViteRunning([string]$Root) {
  $escapedRoot = [Regex]::Escape((Resolve-Path $Root).Path)
  return [bool](Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -eq 'node.exe' -and $_.CommandLine -match $escapedRoot -and
    $_.CommandLine -match 'vite[\\/]bin[\\/]vite\.js'
  } | Select-Object -First 1)
}

Write-Host 'Checking generated configuration reference...'
& (Join-Path $projectRoot 'scripts\update-configuration-reference.ps1') -Check

Write-Host 'Checking release version and capability alignment...'
Invoke-VerificationScript 'scripts\verify-release-alignment.ps1' 'Release alignment failed'

Write-Host 'Checking Android guardrails...'
Invoke-VerificationScript 'scripts\verify-android-guardrails.ps1' 'Android guardrails failed'

if (Test-WorkspaceViteRunning $frontendRoot) {
  Write-Host 'Workspace Vite is running; keeping its node_modules unchanged during verification.'
} else {
  Write-Host 'Restoring exact frontend dependencies...'
  Invoke-Npm @('--prefix', $frontendRoot, 'ci', '--no-audit', '--no-fund') 'npm ci failed'
}

Write-Host 'Building frontend...'
Invoke-Npm @('--prefix', $frontendRoot, 'run', 'build') 'Frontend build failed'

Write-Host 'Checking browser JavaScript syntax...'
$javascriptFiles = @(
  'app.js', 'asset-library.js', 'asset-tag-state.js', 'diagnostics.js', 'export.js', 'media-importer.js',
  'updates.js'
)
foreach ($name in $javascriptFiles) {
  & node --check (Join-Path $frontendRoot "public\$name")
  if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed: $name" }
}

Write-Host 'Running frontend behavior tests...'
Invoke-Npm @('--prefix', $frontendRoot, 'test') 'Frontend behavior tests failed'

Write-Host 'Running backend tests...'
$previousErrorAction = $ErrorActionPreference
try {
  $ErrorActionPreference = 'Continue'
  & (Join-Path $projectRoot 'mvnw.cmd') test 2>&1 | ForEach-Object { Write-Host $_ }
  $mavenExitCode = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $previousErrorAction
}
if ($mavenExitCode -ne 0) { throw 'Backend tests failed' }

Write-Host 'VERIFY SUCCESS: frontend, backend, release alignment and Android guardrails passed.'
