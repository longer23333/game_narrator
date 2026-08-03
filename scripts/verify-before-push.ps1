$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$frontendRoot = Join-Path $projectRoot 'frontend'

Write-Host 'Restoring exact frontend dependencies...'
& $npm --prefix $frontendRoot ci --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }

Write-Host 'Building frontend...'
& $npm --prefix $frontendRoot run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }

Write-Host 'Checking browser JavaScript syntax...'
$javascriptFiles = @(
  'app.js', 'asset-library.js', 'diagnostics.js', 'export.js', 'media-importer.js'
)
foreach ($name in $javascriptFiles) {
  & node --check (Join-Path $frontendRoot "public\$name")
  if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed: $name" }
}

Write-Host 'Running backend tests...'
& (Join-Path $projectRoot 'mvnw.cmd') test
if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }

Write-Host 'VERIFY SUCCESS: this revision is ready to commit and push.'
