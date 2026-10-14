$ErrorActionPreference = 'Stop'

# Keep Vietnamese logs readable in Windows PowerShell / VS Code terminal.
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$Root = Split-Path $PSScriptRoot -Parent

& (Join-Path $PSScriptRoot 'Ensure-ToolPaths.ps1')
& (Join-Path $PSScriptRoot 'start-db.ps1')
& (Join-Path $PSScriptRoot 'Import-Env.ps1')

# Prevent Windows/JDK from sending the legacy Asia/Saigon alias to PostgreSQL and disable DevTools restart crash on file changes.
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=Asia/Ho_Chi_Minh -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dspring.devtools.restart.enabled=false'

# Free port 8080 if lingering from a previous session
try {
    $port8080 = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($port8080) {
        $pids = $port8080 | ForEach-Object { $_.OwningProcess } | Select-Object -Unique
        foreach ($pidToKill in $pids) {
            Write-Host "Port 8080 dang duoc su dung boi PID $pidToKill. Tu dong giai phong port..." -ForegroundColor Yellow
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    }
} catch {
    # Ignore any inspection failure
}

Set-Location (Join-Path $Root 'backend')
Write-Host 'Starting Spring Boot on http://localhost:8080 ...' -ForegroundColor Cyan
Write-Host 'Success means you see: Tomcat started on port 8080 + Started LibraryManagementBackendApplication' -ForegroundColor DarkGray

& .\mvnw.cmd spring-boot:run
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    throw 'Backend stopped before staying online. Read the last "Caused by:" line above; BUILD SUCCESS alone does not mean the server started.'
}

exit $exitCode
