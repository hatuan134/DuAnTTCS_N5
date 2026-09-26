$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

& (Join-Path $PSScriptRoot 'Ensure-Env.ps1')
& (Join-Path $PSScriptRoot 'Import-Env.ps1')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI not found. Install/open Docker Desktop first.'
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not running. Open Docker Desktop and wait until it is ready.'
}

$existingContainer = docker ps --filter 'name=duanttcs_n5_postgres' --format '{{.Names}}' 2>$null
if (-not $existingContainer) {
    $listeners = Get-NetTCPConnection -LocalPort ([int]$env:DB_PORT) -State Listen -ErrorAction SilentlyContinue
    if ($listeners) {
        $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
        throw "Port $env:DB_PORT is already in use (PID: $pids). Stop the old PostgreSQL/service/process first."
    }
}

Write-Host 'Starting PostgreSQL Docker...' -ForegroundColor Cyan
docker compose up -d postgres
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose up failed.'
}

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
    throw 'PostgreSQL did not become healthy.'
}

# A Docker volume can outlive .env. Synchronize the postgres password with
# the current .env so a regenerated local config cannot break JDBC login.
$escapedPassword = $env:DB_PASSWORD.Replace("'", "''")
$escapedUser = $env:DB_USERNAME.Replace('"', '""')
$sql = "ALTER ROLE `"$escapedUser`" WITH PASSWORD '$escapedPassword';"
$sql | docker exec -i duanttcs_n5_postgres psql -U $env:DB_USERNAME -d postgres -v ON_ERROR_STOP=1 *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not synchronize PostgreSQL password with .env.'
}

# On an old volume, POSTGRES_DB is not created again when .env changes.
$escapedDb = $env:DB_NAME.Replace("'", "''")
$dbExists = docker exec duanttcs_n5_postgres psql -U $env:DB_USERNAME -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='$escapedDb';" 2>$null
if (($dbExists | Out-String).Trim() -ne '1') {
    Write-Host "Creating database $env:DB_NAME..." -ForegroundColor Yellow
    docker exec duanttcs_n5_postgres createdb -U $env:DB_USERNAME $env:DB_NAME
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create database $env:DB_NAME."
    }
}

Write-Host "PostgreSQL READY: 127.0.0.1:$env:DB_PORT / $env:DB_NAME" -ForegroundColor Green
