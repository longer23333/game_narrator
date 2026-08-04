param([string]$InstallRoot, [int]$Port = 18081, [switch]$BackendSmoke)
$ErrorActionPreference='Stop'
if (-not $InstallRoot) {
  $InstallRoot = Join-Path $env:LOCALAPPDATA 'Programs\GameNarrator'
  if (-not (Test-Path -LiteralPath (Join-Path $InstallRoot 'GameNarrator.exe') -PathType Leaf)) {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $setup = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'dist') -Filter 'GameNarrator-Setup-*.exe' -File `
      -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $setup) { throw 'GameNarrator is not installed and no setup executable exists under dist. Build it first.' }
    $InstallRoot = Join-Path $projectRoot ('target\clean-install-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Write-Host "No existing installation found. Installing $($setup.Name) into isolated smoke root: $InstallRoot"
    $installer = Start-Process -FilePath $setup.FullName `
      -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART',('/DIR="'+$InstallRoot+'"')) `
      -Wait -PassThru -WindowStyle Hidden
    if ($installer.ExitCode -ne 0) { throw "Installer failed with exit code $($installer.ExitCode)" }
  }
}
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$required = @(
  'GameNarrator.exe','runtime\bin\java.exe','app\game-narrator.jar','tools\ffmpeg\bin\ffmpeg.exe',
  'tools\whisper\Release\whisper-cli.exe','tools\piper\piper\piper.exe','tools\yt-dlp\yt-dlp.exe',
  'models\whisper\ggml-base.bin','models\piper\zh_CN-huayan-medium.onnx'
)
$missing = $required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $InstallRoot $_) -PathType Leaf) }
if ($missing) { throw "Missing packaged components: $($missing -join ', ')" }
foreach($command in @(
  @('runtime\bin\java.exe','-version'), @('tools\ffmpeg\bin\ffmpeg.exe','-version'),
  @('tools\whisper\Release\whisper-cli.exe','--help'), @('tools\piper\piper\piper.exe','--help'),
  @('tools\yt-dlp\yt-dlp.exe','--version')
)) {
  $exe=Join-Path $InstallRoot $command[0]; $p=Start-Process -FilePath $exe -ArgumentList $command[1] -Wait -PassThru -WindowStyle Hidden
  if($p.ExitCode -ne 0){throw "Packaged command failed: $($command[0])"}
}
Write-Host 'Packaged runtime smoke test passed without PATH dependencies.'
if ($BackendSmoke) {
  $smokeData = Join-Path $InstallRoot 'data'
  New-Item -ItemType Directory -Path $smokeData -Force | Out-Null
  $env:GAME_NARRATOR_APP_ROOT=$InstallRoot; $env:GAME_NARRATOR_DATA_ROOT=$smokeData
  $env:OLLAMA_BASE_URL='http://127.0.0.1:19999'
  $env:SERVER_PORT=$Port.ToString()
  $stdout=Join-Path $smokeData 'stdout.log'; $stderr=Join-Path $smokeData 'stderr.log'
  $jarPath=Join-Path $InstallRoot 'app\game-narrator.jar'
  $backend=Start-Process -FilePath (Join-Path $InstallRoot 'runtime\bin\java.exe') `
    -ArgumentList @('-Dfile.encoding=UTF-8','-jar',$jarPath,'--spring.profiles.active=release') `
    -WorkingDirectory $InstallRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
    -PassThru -WindowStyle Hidden
  try {
    $health=$null
    for($attempt=0;$attempt -lt 120;$attempt++) {
      if ($backend.HasExited) { break }
      try { $health=Invoke-RestMethod "http://127.0.0.1:$Port/api/debug/health" -TimeoutSec 2; break } catch { Start-Sleep -Milliseconds 500 }
    }
    if(-not $health) {
      if(Test-Path $stdout){Get-Content $stdout -Tail 100}
      if(Test-Path $stderr){Get-Content $stderr -Tail 100}
      throw 'Packaged backend health check timed out'
    }
    if(-not $health.ffmpegAvailable -or -not $health.whisperAvailable -or -not $health.mediaImporterAvailable) {
      $health | ConvertTo-Json -Depth 4; throw 'Packaged health report shows a missing bundled runtime'
    }
    Write-Host 'Packaged Spring Boot release profile health check passed.'
  } finally {
    if(-not $backend.HasExited) { Stop-Process -Id $backend.Id -Force; $backend.WaitForExit() }
  }
}
Write-Host 'Launch GameNarrator.exe and verify the first-run UI, then run:'
Write-Host "Invoke-RestMethod http://127.0.0.1:$Port/api/debug/health"
