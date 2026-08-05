$ErrorActionPreference = 'Stop'
$appRoot = $PSScriptRoot
$dataRoot = Join-Path $env:LOCALAPPDATA 'GameNarratorDemoLite'
$logRoot = Join-Path $dataRoot 'logs'
New-Item -ItemType Directory -Path $logRoot,(Join-Path $dataRoot 'storage') -Force | Out-Null

$env:GAME_NARRATOR_APP_ROOT = $appRoot
$env:GAME_NARRATOR_DATA_ROOT = $dataRoot
$env:SERVER_PORT = '18083'
$env:FFMPEG_COMMAND = Join-Path $appRoot 'tools\ffmpeg\bin\ffmpeg.exe'
$env:OCR_ENABLED = 'false'
$env:ASSET_SEMANTIC_SEARCH_ENABLED = 'false'
$env:VIDEO_ENCODER = 'libx264'

$java = Join-Path $appRoot 'runtime\bin\java.exe'
$jar = Join-Path $appRoot 'app\game-narrator.jar'
$stdout = Join-Path $logRoot 'application-console.log'
$stderr = Join-Path $logRoot 'application-error.log'
$arguments = @('-Xms48m','-Xmx384m','-XX:+UseSerialGC','-Dfile.encoding=UTF-8',
  '-jar',$jar,'--spring.profiles.active=release,lite')
Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $appRoot `
  -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null

$url = 'http://127.0.0.1:18083'
for ($attempt = 0; $attempt -lt 90; $attempt++) {
  try {
    $response = Invoke-WebRequest -UseBasicParsing "$url/api/debug/health" -TimeoutSec 1
    if ($response.StatusCode -eq 200) { Start-Process $url; exit 0 }
  } catch { }
  Start-Sleep -Milliseconds 500
}
Add-Type -AssemblyName PresentationFramework
[System.Windows.MessageBox]::Show("GameNarrator Demo Lite startup timed out. Logs: $logRoot",
  'Startup failed') | Out-Null
exit 1
