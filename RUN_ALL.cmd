@echo off
setlocal
cd /d "%~dp0"

if exist "C:\Program Files\nodejs" set "PATH=C:\Program Files\nodejs;%PATH%"
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin" (
  set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin;%PATH%"
  if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
)

powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\start-db.ps1"
if errorlevel 1 (
  echo.
  echo DATABASE START FAILED.
  pause
  exit /b 1
)

start "LIBRA Backend" powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\run-backend.ps1"
start "LIBRA Frontend" powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\run-frontend.ps1"

timeout /t 4 /nobreak >nul
start "" "http://localhost:5173"

endlocal
