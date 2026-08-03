@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-deepseek-context.ps1" %*
if errorlevel 1 exit /b %errorlevel%
echo.
echo DeepSeek project context is up to date.
