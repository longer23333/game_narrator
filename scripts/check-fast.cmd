@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-fast.ps1" %*
