@echo off
setlocal
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-local-cloud.ps1" -StartBackend
if errorlevel 1 (
  echo.
  echo 启动失败，请根据上方中文提示处理后重试。
  pause
  exit /b 1
)
endlocal
