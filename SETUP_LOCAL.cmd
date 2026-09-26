@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\init-local.ps1"
if errorlevel 1 (
  echo.
  echo SETUP FAILED. Read the error above.
  pause
  exit /b 1
)
echo.
echo SETUP COMPLETE.
pause
