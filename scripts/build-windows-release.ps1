param(
  [switch]$InstallInnoSetup,
  [switch]$SkipDownloads,
  [string]$LocalDependenciesRoot = $env:GAME_NARRATOR_BUILD_DEPS_ROOT
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseRoot = Join-Path $projectRoot 'release'
$staging = Join-Path $releaseRoot 'staging'
$cache = Join-Path $releaseRoot 'cache'
$dist = Join-Path $projectRoot 'dist'

function Reset-OwnedDirectory([string]$Path, [string]$RequiredParent) {
  $full = [IO.Path]::GetFullPath($Path)
  $parent = [IO.Path]::GetFullPath($RequiredParent).TrimEnd('\') + '\'
  if (-not $full.StartsWith($parent, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to reset path outside $RequiredParent" }
  if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
  New-Item -ItemType Directory -Path $full | Out-Null
}
function Download([string]$Url, [string]$Destination) {
  if (Test-Path -LiteralPath $Destination) { return }
  if (-not [string]::IsNullOrWhiteSpace($LocalDependenciesRoot)) {
    $externalCacheFile = Join-Path ([IO.Path]::GetFullPath($LocalDependenciesRoot)) (Join-Path 'release\cache' (Split-Path -Leaf $Destination))
    if (Test-Path -LiteralPath $externalCacheFile -PathType Leaf) {
      Copy-Item -LiteralPath $externalCacheFile -Destination $Destination
      return
    }
  }
  if ($SkipDownloads) { throw "Missing cached dependency: $Destination" }
  Write-Host "Downloading $Url"
  $partial = "$Destination.part"
  & curl.exe --fail --location --retry 5 --retry-delay 3 --continue-at - --user-agent 'GameNarrator-ReleaseBuilder' --output $partial $Url
  if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
  Move-Item -LiteralPath $partial -Destination $Destination
}
function Require-File([string]$Path, [string]$Hint) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Hint ($Path)" }
}
function Download-GitHubText([string]$Repository, [string]$RemotePath, [string]$Destination) {
  if (Test-Path -LiteralPath $Destination) { return }
  if (-not [string]::IsNullOrWhiteSpace($LocalDependenciesRoot)) {
    $externalCacheFile = Join-Path ([IO.Path]::GetFullPath($LocalDependenciesRoot)) (Join-Path 'release\cache' (Split-Path -Leaf $Destination))
    if (Test-Path -LiteralPath $externalCacheFile -PathType Leaf) {
      Copy-Item -LiteralPath $externalCacheFile -Destination $Destination
      return
    }
  }
  if ($SkipDownloads) { throw "Missing cached license: $Destination" }
  $response = Invoke-RestMethod -Headers @{'User-Agent'='GameNarrator-ReleaseBuilder'} -Uri "https://api.github.com/repos/$Repository/contents/$RemotePath"
  [IO.File]::WriteAllBytes($Destination, [Convert]::FromBase64String(($response.content -replace '\s','')))
}
function Copy-Directory([string]$Source, [string]$Destination) {
  if (-not (Test-Path -LiteralPath $Source -PathType Container)) { throw "Missing directory: $Source" }
  New-Item -ItemType Directory -Path $Destination -Force | Out-Null
  Copy-Item -Path (Join-Path $Source '*') -Destination $Destination -Recurse -Force
}
function Resolve-LocalDependency([string]$RelativePath) {
  $projectPath = Join-Path $projectRoot $RelativePath
  if (Test-Path -LiteralPath $projectPath) { return $projectPath }
  if (-not [string]::IsNullOrWhiteSpace($LocalDependenciesRoot)) {
    $externalPath = Join-Path ([IO.Path]::GetFullPath($LocalDependenciesRoot)) $RelativePath
    if (Test-Path -LiteralPath $externalPath) { return $externalPath }
  }
  throw "Missing local dependency '$RelativePath'. Run the matching setup script or set GAME_NARRATOR_BUILD_DEPS_ROOT."
}
function Copy-WhisperRuntime([string]$Source, [string]$Destination) {
  $releaseSource = Join-Path $Source 'Release'
  $releaseDestination = Join-Path $Destination 'Release'
  Require-File (Join-Path $releaseSource 'whisper-cli.exe') 'Whisper CLI is missing'
  New-Item -ItemType Directory -Path $releaseDestination -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $releaseSource 'whisper-cli.exe') -Destination $releaseDestination
  Get-ChildItem -LiteralPath $releaseSource -Filter '*.dll' -File | Where-Object {
    $_.Name -notin @('parakeet.dll', 'SDL2.dll')
  } | Copy-Item -Destination $releaseDestination
}

if ([string]::IsNullOrWhiteSpace($LocalDependenciesRoot)) {
  $siblingDependencies = Join-Path (Split-Path -Parent $projectRoot) 'game_narrator-local-20260803'
  if (Test-Path -LiteralPath $siblingDependencies -PathType Container) {
    $LocalDependenciesRoot = $siblingDependencies
  }
}

New-Item -ItemType Directory -Path $cache -Force | Out-Null
Reset-OwnedDirectory $staging $releaseRoot
if (-not (Test-Path -LiteralPath $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

Write-Host 'Building frontend and Spring Boot application...'
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$frontendRoot = Join-Path $projectRoot 'frontend'
Require-File (Join-Path $frontendRoot 'package-lock.json') 'Frontend lock file is required for reproducible builds'
Write-Host 'Restoring locked frontend dependencies...'
& $npm --prefix $frontendRoot ci --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency restore failed' }
& $npm --prefix (Join-Path $projectRoot 'frontend') run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
& (Join-Path $projectRoot 'mvnw.cmd') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
$jar = Get-ChildItem (Join-Path $projectRoot 'target') -Filter '*.jar' | Where-Object Name -NotLike '*.original' | Select-Object -First 1
if (-not $jar) { throw 'Spring Boot jar was not generated' }
New-Item -ItemType Directory -Path (Join-Path $staging 'app') | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $staging 'app\game-narrator.jar')

Write-Host 'Creating bundled Java 21 runtime...'
$jlink = (Get-Command jlink -ErrorAction Stop).Source
& $jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $staging 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

Write-Host 'Publishing self-contained Windows launcher...'
& dotnet publish (Join-Path $projectRoot 'launcher\GameNarrator.Launcher.csproj') -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o (Join-Path $staging 'launcher-build')
if ($LASTEXITCODE -ne 0) { throw 'Launcher publish failed' }
Copy-Item -LiteralPath (Join-Path $staging 'launcher-build\GameNarrator.exe') -Destination (Join-Path $staging 'GameNarrator.exe')
Remove-Item -LiteralPath (Join-Path $staging 'launcher-build') -Recurse -Force

Write-Host 'Preparing Microsoft Edge WebView2 prerequisite...'
$webViewBootstrapper = Join-Path $cache 'MicrosoftEdgeWebview2Setup.exe'
Download 'https://go.microsoft.com/fwlink/p/?LinkId=2124703' $webViewBootstrapper
New-Item -ItemType Directory -Path (Join-Path $staging 'prerequisites') -Force | Out-Null
Copy-Item -LiteralPath $webViewBootstrapper -Destination (Join-Path $staging 'prerequisites\MicrosoftEdgeWebview2Setup.exe')

$ffmpegTag = 'latest'
$ffmpegZip = Join-Path $cache 'ffmpeg-master-latest-win64-gpl.zip'
Download 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip' $ffmpegZip
$ffmpegExtract = Join-Path $cache 'ffmpeg-extracted'
Reset-OwnedDirectory $ffmpegExtract $cache
Expand-Archive -LiteralPath $ffmpegZip -DestinationPath $ffmpegExtract -Force
$ffmpegExe = Get-ChildItem $ffmpegExtract -Recurse -Filter 'ffmpeg.exe' | Select-Object -First 1
if (-not $ffmpegExe) { throw 'Downloaded FFmpeg archive has no ffmpeg.exe' }
Copy-Directory $ffmpegExe.Directory.FullName (Join-Path $staging 'tools\ffmpeg\bin')

$ollamaVersion = 'v0.32.5'
$whisperSource = Resolve-LocalDependency 'tools\whisper'
$piperSource = Resolve-LocalDependency 'tools\piper'
$ytDlpSource = Resolve-LocalDependency 'tools\yt-dlp\yt-dlp.exe'
$whisperModelSource = Resolve-LocalDependency 'models\ggml-base.bin'
$piperModelSource = Resolve-LocalDependency 'models\piper'

Copy-WhisperRuntime $whisperSource (Join-Path $staging 'tools\whisper')
Copy-Directory $piperSource (Join-Path $staging 'tools\piper')
New-Item -ItemType Directory -Path (Join-Path $staging 'tools\yt-dlp') -Force | Out-Null
Copy-Item -LiteralPath $ytDlpSource -Destination (Join-Path $staging 'tools\yt-dlp\yt-dlp.exe')
New-Item -ItemType Directory -Path (Join-Path $staging 'models\whisper'),(Join-Path $staging 'models\piper') -Force | Out-Null
Copy-Item -LiteralPath $whisperModelSource -Destination (Join-Path $staging 'models\whisper\ggml-base.bin')
Copy-Item -Path (Join-Path $piperModelSource '*') -Destination (Join-Path $staging 'models\piper') -Force

$licenseDir = Join-Path $staging 'licenses'
New-Item -ItemType Directory -Path $licenseDir | Out-Null
Download-GitHubText 'BtbN/FFmpeg-Builds' 'LICENSE' (Join-Path $cache 'FFmpeg-Builds-LICENSE.txt')
Download-GitHubText 'ollama/ollama' 'LICENSE' (Join-Path $cache 'Ollama-LICENSE.txt')
Download-GitHubText 'ggml-org/whisper.cpp' 'LICENSE' (Join-Path $cache 'WhisperCpp-LICENSE.txt')
Download-GitHubText 'yt-dlp/yt-dlp' 'LICENSE' (Join-Path $cache 'yt-dlp-LICENSE.txt')
Download-GitHubText 'jrsoftware/issrc' 'Files/Languages/ChineseSimplified.isl' (Join-Path $cache 'ChineseSimplified.isl')
Copy-Item -Path (Join-Path $cache '*LICENSE.txt') -Destination $licenseDir

$lock = [ordered]@{
  generatedAt = (Get-Date).ToUniversalTime().ToString('o')
  platform = 'windows-x64'
  dependencies = @(
    @{name='ffmpeg'; source='BtbN/FFmpeg-Builds latest'; sha256=(Get-FileHash $ffmpegZip -Algorithm SHA256).Hash; archiveBytes=(Get-Item $ffmpegZip).Length},
    @{name='ollama'; version=$ollamaVersion; delivery='first-run-resumable-download'; sha256='7c941ae084569d298062d29f8139163a3187c76dbca0479c70d085e78fd8c7bb'},
    @{name='webview2-evergreen-bootstrapper'; source='Microsoft'; sha256=(Get-FileHash $webViewBootstrapper -Algorithm SHA256).Hash},
    @{name='whisper-model-base'; sha256=(Get-FileHash $whisperModelSource -Algorithm SHA256).Hash},
    @{name='piper-huayan-medium'; sha256=(Get-FileHash (Join-Path $piperModelSource 'zh_CN-huayan-medium.onnx') -Algorithm SHA256).Hash},
    @{name='yt-dlp'; sha256=(Get-FileHash $ytDlpSource -Algorithm SHA256).Hash}
  )
}
$lock | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $staging 'dependency-lock.json') -Encoding utf8

$iscc = Get-Command iscc -ErrorAction SilentlyContinue
if (-not $iscc -and $InstallInnoSetup) {
  Write-Host 'Installing Inno Setup compiler with winget...'
  & winget install --id JRSoftware.InnoSetup -e --accept-package-agreements --accept-source-agreements --silent
  if ($LASTEXITCODE -ne 0) { throw 'Inno Setup installation failed' }
  $candidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe'),
    (Join-Path $env:ProgramFiles 'Inno Setup 6\ISCC.exe'),
    (Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe')
  )
  $candidate = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
  if ($candidate) { $iscc = Get-Item $candidate }
}
if (-not $iscc) {
  $candidate = Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe'
  if (Test-Path -LiteralPath $candidate) { $iscc = Get-Item $candidate }
}
if (-not $iscc) { throw 'Inno Setup 6 is required. Re-run with -InstallInnoSetup.' }
Write-Host 'Compiling GameNarrator-Setup.exe...'
$isccPath = if ($iscc -is [IO.FileInfo]) { $iscc.FullName } else { $iscc.Source }
& $isccPath (Join-Path $releaseRoot 'installer\GameNarrator.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed' }
$setup = Join-Path $dist 'GameNarrator-Setup.exe'
Require-File $setup 'Installer was not generated'
Write-Host "SUCCESS: $setup"
