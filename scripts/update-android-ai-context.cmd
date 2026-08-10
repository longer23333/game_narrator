@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-android-ai-context.ps1" %*
if errorlevel 1 exit /b %errorlevel%
echo.
echo Android AI context is up to date.
