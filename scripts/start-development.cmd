@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-development.ps1"
if errorlevel 1 (
  echo.
  echo 启动失败，请查看 target\dev-logs 中的日志。
  pause
)
