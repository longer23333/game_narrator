@echo off
setlocal
set "APP_ROOT=%~dp0"
set "DATA_ROOT=%LOCALAPPDATA%\GameNarratorDemoLite"
if not exist "%DATA_ROOT%\logs" mkdir "%DATA_ROOT%\logs"
if not exist "%DATA_ROOT%\storage" mkdir "%DATA_ROOT%\storage"
set "GAME_NARRATOR_APP_ROOT=%APP_ROOT:~0,-1%"
set "GAME_NARRATOR_DATA_ROOT=%DATA_ROOT%"
set "SERVER_PORT=18083"
set "FFMPEG_COMMAND=%APP_ROOT%tools\ffmpeg\bin\ffmpeg.exe"
set "OCR_ENABLED=false"
set "ASSET_SEMANTIC_SEARCH_ENABLED=false"
set "VIDEO_ENCODER=libx264"
start "GameNarrator Demo Lite" /b "%APP_ROOT%runtime\bin\javaw.exe" -Xms48m -Xmx384m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -jar "%APP_ROOT%app\game-narrator.jar" --spring.profiles.active=release,lite
powershell.exe -NoProfile -WindowStyle Hidden -Command "$u='http://127.0.0.1:18083'; for($i=0;$i -lt 60;$i++){try{Invoke-WebRequest -UseBasicParsing ($u+'/api/debug/health') -TimeoutSec 1|Out-Null; Start-Process $u; exit 0}catch{Start-Sleep -Milliseconds 500}}; Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show('GameNarrator 展示版启动超时，请查看 '+$env:LOCALAPPDATA+'\GameNarratorDemoLite\logs','启动失败')|Out-Null"
