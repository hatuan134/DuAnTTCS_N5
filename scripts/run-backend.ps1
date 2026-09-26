$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

& (Join-Path $PSScriptRoot 'Import-Env.ps1')
& (Join-Path $PSScriptRoot 'start-db.ps1')

Set-Location (Join-Path $Root 'backend')
Write-Host 'Đang chạy Spring Boot...' -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run
