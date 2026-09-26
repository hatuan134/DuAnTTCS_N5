$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

& (Join-Path $PSScriptRoot 'start-db.ps1')
& (Join-Path $PSScriptRoot 'Import-Env.ps1')

# Prevent Windows/JDK from sending the legacy Asia/Saigon alias to PostgreSQL.
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=Asia/Ho_Chi_Minh'

Set-Location (Join-Path $Root 'backend')
Write-Host 'Starting Spring Boot on http://localhost:8080 ...' -ForegroundColor Cyan
Write-Host 'Success means you see: Tomcat started on port 8080 + Started LibraryManagementBackendApplication' -ForegroundColor DarkGray

& .\mvnw.cmd spring-boot:run
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    throw 'Backend stopped before staying online. Read the last "Caused by:" line above; BUILD SUCCESS alone does not mean the server started.'
}

exit $exitCode
