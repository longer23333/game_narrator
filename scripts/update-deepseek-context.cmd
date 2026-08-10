@echo off
setlocal
set "GAME_NARRATOR_CONTEXT_SCRIPT_ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& ([scriptblock]::Create([IO.File]::ReadAllText('%~dp0export-deepseek-context.ps1', [Text.Encoding]::UTF8))) %*"
if errorlevel 1 exit /b %errorlevel%
echo.
echo DeepSeek project context is up to date.
