@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\run-backend.ps1"
if errorlevel 1 (
  echo.
  echo BACKEND STOPPED OR FAILED. Read the last error above.
  pause
)
