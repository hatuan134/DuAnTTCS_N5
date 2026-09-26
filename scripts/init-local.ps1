$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Write-Host 'LIBRA - one-time local initialization' -ForegroundColor Cyan

$missing = New-Object System.Collections.Generic.List[string]
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { $missing.Add('JDK 17') }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { $missing.Add('Node.js 22') }
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { $missing.Add('npm') }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { $missing.Add('Docker Desktop') }

if ($missing.Count -gt 0) {
    throw ('Missing tools: ' + ($missing -join ', ') + '. Install them first, then rerun SETUP_LOCAL.cmd.')
}

$javaVersionLines = & $env:ComSpec /d /c 'java -version 2>&1'
$javaFirstLine = ($javaVersionLines | Select-Object -First 1) -join ''
$javaMajor = $null
if ($javaFirstLine -match '"(?<major>\d+)(?:\.|"|$)') {
    $javaMajor = [int]$Matches['major']
}
if ($javaMajor -ne 17) {
    throw "Java $javaMajor detected. This project standard is JDK 17. Install Temurin 17 and make it active before continuing."
}

$nodeRaw = (& node -v).Trim().TrimStart('v')
$nodeMajor = [int]($nodeRaw.Split('.')[0])
if ($nodeMajor -lt 22) {
    throw "Node.js $nodeRaw detected. Install Node.js 22 or newer."
}

Write-Host "Java 17 OK / Node $nodeRaw OK" -ForegroundColor Green

& (Join-Path $PSScriptRoot 'Ensure-Env.ps1')
& (Join-Path $PSScriptRoot 'start-db.ps1')

$Frontend = Join-Path $Root 'frontend'
if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Write-Host 'Installing frontend dependencies once...' -ForegroundColor Cyan
    Write-Host 'First clone can take a few minutes.' -ForegroundColor DarkGray
    Set-Location $Frontend
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) {
        throw 'Frontend dependency installation failed. Close all Vite/Node processes and retry.'
    }
}

Set-Location $Root
Write-Host ''
Write-Host 'LOCAL SETUP READY' -ForegroundColor Green
Write-Host 'Daily run: double-click RUN_ALL.cmd' -ForegroundColor Green
