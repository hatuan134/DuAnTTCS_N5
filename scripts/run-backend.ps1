$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

& (Join-Path $PSScriptRoot 'Import-Env.ps1')
& (Join-Path $PSScriptRoot 'start-db.ps1')

# Force the canonical IANA timezone used by PostgreSQL.
# This prevents Windows/JDK from sending the legacy alias "Asia/Saigon"
# during the PostgreSQL JDBC startup handshake.
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=Asia/Ho_Chi_Minh'

Set-Location (Join-Path $Root 'backend')
Write-Host 'Đang chạy Spring Boot...' -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run
