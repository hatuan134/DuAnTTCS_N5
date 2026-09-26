$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Frontend = Join-Path $Root 'frontend'

Set-Location $Frontend

if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Write-Host 'Chưa có node_modules - đang chạy npm ci...' -ForegroundColor Cyan
    npm ci
    if ($LASTEXITCODE -ne 0) {
        throw 'npm ci thất bại.'
    }
}

Write-Host 'Đang chạy Vite tại http://localhost:5173 ...' -ForegroundColor Cyan
npm run dev -- --port 5173 --strictPort
