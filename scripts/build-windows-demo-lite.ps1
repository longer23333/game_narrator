param([switch]$SkipFrontendRestore)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$staging = Join-Path $projectRoot 'release\staging-lite'
$cache = Join-Path $projectRoot 'release\cache'
$dist = Join-Path $projectRoot 'dist'
$pom = [xml](Get-Content (Join-Path $projectRoot 'pom.xml') -Raw)
$version = [string]$pom.project.version

if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging | Out-Null

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
if (-not $SkipFrontendRestore) {
  & $npm --prefix (Join-Path $projectRoot 'frontend') ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'Frontend dependencies failed' }
}
& $npm --prefix (Join-Path $projectRoot 'frontend') run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
Get-ChildItem (Join-Path $projectRoot 'target') -Filter '*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
& (Join-Path $projectRoot 'mvnw.cmd') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
$jar = Join-Path $projectRoot "target\game-narrator-$version.jar"
if (-not (Test-Path $jar)) { throw "Missing $jar" }
New-Item -ItemType Directory -Path (Join-Path $staging 'app') | Out-Null
Copy-Item $jar (Join-Path $staging 'app\game-narrator.jar')

& (Get-Command jlink -ErrorAction Stop).Source --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $staging 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

$ffmpeg = Get-ChildItem (Join-Path $cache 'ffmpeg-extracted') -Recurse -Filter ffmpeg.exe | Select-Object -First 1
if (-not $ffmpeg) { throw 'Run the full release build once to prepare the FFmpeg cache.' }
New-Item -ItemType Directory -Path (Join-Path $staging 'tools\ffmpeg\bin') -Force | Out-Null
Copy-Item $ffmpeg.FullName (Join-Path $staging 'tools\ffmpeg\bin\ffmpeg.exe')
Copy-Item (Join-Path $projectRoot 'release\lite\GameNarrator-Demo-Lite.ps1') $staging

$bytes = (Get-ChildItem $staging -Recurse -File | Measure-Object Length -Sum).Sum
$mib = [math]::Round($bytes / 1MB, 1)
if ($bytes -gt 300MB) { throw "Demo Lite installed size is $mib MiB, exceeding the 300 MiB limit." }
Set-Content (Join-Path $staging 'EDITION.txt') "GameNarrator Demo Lite $version`r`nInstalled payload: $mib MiB`r`nNo bundled AI, Whisper or TTS models."

$iscc = Get-Item (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe') -ErrorAction Stop
& $iscc.FullName "/DAppVersion=$version" (Join-Path $projectRoot 'release\installer\GameNarrator-Demo-Lite.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed' }
$setup = Join-Path $dist "GameNarrator-Demo-Lite-Setup-$version.exe"
if (-not (Test-Path $setup)) { throw 'Installer was not generated' }
Write-Host "SUCCESS: $setup (installed payload $mib MiB)"
