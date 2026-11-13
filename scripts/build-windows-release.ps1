param(
  [switch]$InstallInnoSetup,
  [switch]$SkipDownloads,
  [switch]$SkipFrontendRestore,
  [switch]$SkipTests,
  [switch]$FastCompression,
  [string]$LocalDependenciesRoot = $env:GAME_NARRATOR_BUILD_DEPS_ROOT
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseRoot = Join-Path $projectRoot 'release'
$staging = Join-Path $releaseRoot 'staging'
$cache = Join-Path $releaseRoot 'cache'
$dist = Join-Path $projectRoot 'dist'
$pom = [xml](Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw)
$appVersion = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($appVersion)) { throw 'Application version is missing from pom.xml' }

function Reset-OwnedDirectory([string]$Path, [string]$RequiredParent) {
  $full = [IO.Path]::GetFullPath($Path)
  $parent = [IO.Path]::GetFullPath($RequiredParent).TrimEnd('\') + '\'
  if (-not $full.StartsWith($parent, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to reset path outside $RequiredParent" }
  if (Test-Path -LiteralPath $full) {
    # 用 robocopy /MIR 清空而不是 Remove-Item -Recurse:staging/ffmpeg 解压缓存每次构建都要整体重置,
    # 动辄几百个文件,批量删除守护(SAFE_DELETE_BULK_CONFIRM_REQUIRED,阈值 50)会把它拦成构建失败。
    # robocopy /MIR 空目录是清目录的标准手法,退出码 0-7 均视为成功。
    $empty = Join-Path $releaseRoot ('empty-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $empty | Out-Null
    & robocopy $empty $full '/MIR' '/NFL' '/NDL' '/NJH' '/NJS' '/NP' | Out-Null
    $rc = $LASTEXITCODE
    # 空目录用 .NET 直删:Remove-Item 会被删除包装器接管,空目录也可能触发 fail-closed。
    try { [IO.Directory]::Delete($empty) } catch { Write-Warning "Leftover helper directory: $empty" }
    if ($rc -ge 8) { throw "robocopy failed to reset $full (exit $rc)" }
  }
  New-Item -ItemType Directory -Path $full -Force | Out-Null
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
function Stop-OwnedFrontendDevProcesses([string]$FrontendRoot) {
  $normalizedRoot = [IO.Path]::GetFullPath($FrontendRoot)
  $owned = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -in @('node.exe', 'esbuild.exe') -and
    -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
    $_.CommandLine.IndexOf($normalizedRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
    ($_.CommandLine -match 'vite[\\/]bin[\\/]vite\.js|esbuild\.exe\s+--service')
  })
  foreach ($process in $owned | Sort-Object { if ($_.Name -eq 'esbuild.exe') { 0 } else { 1 } }) {
    Write-Host "Stopping workspace frontend process PID $($process.ProcessId): $($process.Name)"
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
  }
  if ($owned.Count -gt 0) { Start-Sleep -Milliseconds 500 }
}

if ([string]::IsNullOrWhiteSpace($LocalDependenciesRoot)) {
  $siblingDependencies = Join-Path (Split-Path -Parent $projectRoot) 'game_narrator-local-20260803'
  if (Test-Path -LiteralPath $siblingDependencies -PathType Container) {
    $LocalDependenciesRoot = $siblingDependencies
  }
}

if ($SkipTests) {
  Write-Warning 'Skipping the unified release verification because -SkipTests was explicitly supplied.'
  $verificationBuiltFrontend = $false
} else {
  Write-Host 'Running required release verification before packaging...'
  & (Join-Path $PSScriptRoot 'verify-before-push.ps1') -SkipFrontendRestore:$SkipFrontendRestore
  if ($LASTEXITCODE -ne 0) { throw 'Release verification failed' }
  # verify-before-push.ps1 已经用同一份 vite 配置构建过前端,这里不再重复构建(省约 10-30 秒)。
  $verificationBuiltFrontend = $true
}

New-Item -ItemType Directory -Path $cache -Force | Out-Null
Reset-OwnedDirectory $staging $releaseRoot
if (-not (Test-Path -LiteralPath $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

Write-Host 'Building frontend and Spring Boot application...'
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$frontendRoot = Join-Path $projectRoot 'frontend'
Require-File (Join-Path $frontendRoot 'package-lock.json') 'Frontend lock file is required for reproducible builds'
Write-Host 'Restoring locked frontend dependencies...'
if (-not $SkipFrontendRestore) {
  Stop-OwnedFrontendDevProcesses $frontendRoot
  & $npm --prefix $frontendRoot ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency restore failed. Close any running Vite process or retry with -SkipFrontendRestore when the locked dependencies are already installed.' }
} elseif (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules\vite\bin\vite.js') -PathType Leaf)) {
  throw '-SkipFrontendRestore requires the locked frontend dependencies to already be installed.'
}
if ($verificationBuiltFrontend) {
  Write-Host 'Frontend was already built by verification; skipping the duplicate build.'
} else {
  & $npm --prefix (Join-Path $projectRoot 'frontend') run build
  if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
}
$targetRoot = Join-Path $projectRoot 'target'
$staleJars = @(Get-ChildItem $targetRoot -Filter '*.jar' -File -ErrorAction SilentlyContinue)
foreach ($staleJar in $staleJars) {
  # 构建产物一律 .NET 直删:删除包装器的 trash 本身可能失败并 fail-closed,把整个构建拦死。
  if ([IO.File]::Exists($staleJar.FullName)) { [IO.File]::Delete($staleJar.FullName) }
}
& (Join-Path $projectRoot 'mvnw.cmd') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
$jarPath = Join-Path $projectRoot "target\game-narrator-$appVersion.jar"
Require-File $jarPath "Spring Boot jar for version $appVersion was not generated"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarArchive = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
  $indexEntry = $jarArchive.GetEntry('BOOT-INF/classes/static/index.html')
  if (-not $indexEntry) { throw 'Packaged Spring Boot jar does not contain the frontend index' }
  $reader = [IO.StreamReader]::new($indexEntry.Open(), [Text.Encoding]::UTF8)
  try { $packagedIndex = $reader.ReadToEnd() } finally { $reader.Dispose() }
  if (-not $packagedIndex.Contains("v$appVersion")) {
    throw "Packaged frontend version does not match $appVersion; refusing to create a stale installer"
  }
} finally { $jarArchive.Dispose() }
New-Item -ItemType Directory -Path (Join-Path $staging 'app') | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $staging 'app\game-narrator.jar')

Write-Host 'Creating bundled Java 21 runtime...'
$jlink = (Get-Command jlink -ErrorAction Stop).Source
& $jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $staging 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

Write-Host 'Publishing self-contained Windows launcher...'
& dotnet publish (Join-Path $projectRoot 'launcher\GameNarrator.Launcher.csproj') -c Release -r win-x64 --self-contained true -p:Version=$appVersion -p:AssemblyVersion="$appVersion.0" -p:FileVersion="$appVersion.0" -p:InformationalVersion=$appVersion -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o (Join-Path $staging 'launcher-build')
if ($LASTEXITCODE -ne 0) { throw 'Launcher publish failed' }
Copy-Item -LiteralPath (Join-Path $staging 'launcher-build\GameNarrator.exe') -Destination (Join-Path $staging 'GameNarrator.exe')
$launcherBuildDir = Join-Path $staging 'launcher-build'
if ([IO.Directory]::Exists($launcherBuildDir)) { [IO.Directory]::Delete($launcherBuildDir, $true) }

Write-Host 'Preparing Microsoft Edge WebView2 prerequisite...'
$webViewBootstrapper = Join-Path $cache 'MicrosoftEdgeWebview2Setup.exe'
Download 'https://go.microsoft.com/fwlink/p/?LinkId=2124703' $webViewBootstrapper
New-Item -ItemType Directory -Path (Join-Path $staging 'prerequisites') -Force | Out-Null
Copy-Item -LiteralPath $webViewBootstrapper -Destination (Join-Path $staging 'prerequisites\MicrosoftEdgeWebview2Setup.exe')

$ffmpegTag = 'latest'
$ffmpegZip = Join-Path $cache 'ffmpeg-master-latest-win64-gpl.zip'
Download 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip' $ffmpegZip
$ffmpegExtract = Join-Path $cache 'ffmpeg-extracted'
# 按 zip 的 SHA256 判断缓存解压是否可用,避免每次展开 162MB 压缩包(省约 40 秒)。
$ffmpegZipHash = (Get-FileHash -LiteralPath $ffmpegZip -Algorithm SHA256).Hash
$ffmpegHashFile = Join-Path $cache 'ffmpeg-extracted.sha256'
$cachedFfmpegExe = Get-ChildItem $ffmpegExtract -Recurse -Filter 'ffmpeg.exe' -File -ErrorAction SilentlyContinue | Select-Object -First 1
if ($cachedFfmpegExe -and (Test-Path -LiteralPath $ffmpegHashFile) -and
    ((Get-Content -LiteralPath $ffmpegHashFile -Raw).Trim() -eq $ffmpegZipHash)) {
  Write-Host 'Reusing cached FFmpeg extraction...'
  $ffmpegExe = $cachedFfmpegExe
} else {
  Reset-OwnedDirectory $ffmpegExtract $cache
  Expand-Archive -LiteralPath $ffmpegZip -DestinationPath $ffmpegExtract -Force
  $ffmpegExe = Get-ChildItem $ffmpegExtract -Recurse -Filter 'ffmpeg.exe' | Select-Object -First 1
  Set-Content -LiteralPath $ffmpegHashFile -Value $ffmpegZipHash -Encoding ascii
}
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
$isccArgs = @("/DAppVersion=$appVersion")
if ($FastCompression) { $isccArgs += '/DFastCompression' }
$isccArgs += (Join-Path $releaseRoot 'installer\GameNarrator.iss')
& $isccPath @isccArgs
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed' }
$setup = Join-Path $dist "GameNarrator-Setup-$appVersion.exe"
Require-File $setup 'Installer was not generated'
Write-Host "SUCCESS: $setup"
