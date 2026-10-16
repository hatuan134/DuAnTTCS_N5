@echo off
cd /d "%~dp0"

if exist "C:\Program Files\nodejs" set "PATH=C:\Program Files\nodejs;%PATH%"

powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\run-frontend.ps1"
if errorlevel 1 (
  echo.
  echo FRONTEND STOPPED OR FAILED. Read the last error above.
  pause
)
