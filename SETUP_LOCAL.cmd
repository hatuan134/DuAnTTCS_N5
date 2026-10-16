@echo off
cd /d "%~dp0"

if exist "C:\Program Files\nodejs" set "PATH=C:\Program Files\nodejs;%PATH%"
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin" (
  set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin;%PATH%"
  if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
)

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
