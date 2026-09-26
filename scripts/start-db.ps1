$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

Set-Location $Root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Không tìm thấy Docker CLI. Hãy cài/mở Docker Desktop trước.'
}

if (-not (Test-Path (Join-Path $Root '.env'))) {
    throw 'Chưa có .env. Hãy chạy .\setup.ps1 hoặc tạo .env từ .env.example.'
}

& (Join-Path $PSScriptRoot 'Import-Env.ps1')

# Báo sớm nếu port host đang bị ứng dụng khác chiếm.
$existingContainer = docker ps --filter 'name=duanttcs_n5_postgres' --format '{{.Names}}' 2>$null
if (-not $existingContainer) {
    $listeners = Get-NetTCPConnection -LocalPort ([int]$env:DB_PORT) -State Listen -ErrorAction SilentlyContinue
    if ($listeners) {
        $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', ' 
        throw "Port $env:DB_PORT đang bị chiếm (PID: $pids). Nếu là PostgreSQL cài trực tiếp trên Windows, hãy Stop service đó rồi chạy lại."
    }
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine chưa chạy. Hãy mở Docker Desktop và chờ Engine Running.'
}

Write-Host 'Khởi động PostgreSQL container...' -ForegroundColor Cyan
docker compose up -d postgres
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up thất bại. Kiểm tra Docker Desktop và port $env:DB_PORT."
}

Write-Host 'Chờ PostgreSQL healthy...' -ForegroundColor Cyan
$healthy = $false
for ($i = 0; $i -lt 60; $i++) {
    $status = docker inspect --format '{{.State.Health.Status}}' duanttcs_n5_postgres 2>$null
    if ($status -eq 'healthy') {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $healthy) {
    docker compose ps
    docker compose logs --tail 80 postgres
    throw 'PostgreSQL chưa đạt trạng thái healthy.'
}

Write-Host 'PostgreSQL đã sẵn sàng tại localhost:'$env:DB_PORT -ForegroundColor Green
docker compose ps
