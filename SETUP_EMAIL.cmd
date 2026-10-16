@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Configure-Mail.ps1"
if errorlevel 1 (
  echo.
  echo EMAIL SETUP FAILED. Read the error above.
  pause
  exit /b 1
)

echo.
echo EMAIL SETUP COMPLETED.
echo You can now run: .\RUN_ALL.cmd
pause
