$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Frontend = Join-Path $Root 'frontend'
Set-Location $Frontend

& (Join-Path $PSScriptRoot 'Ensure-ToolPaths.ps1')

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    if (Test-Path 'C:\Program Files\nodejs\node.exe') {
        $env:PATH = "C:\Program Files\nodejs;$env:PATH"
    } else {
        throw 'Node.js not found. Install Node.js first.'
    }
}

if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    if (Test-Path 'C:\Program Files\nodejs\npm.cmd') {
        $env:PATH = "C:\Program Files\nodejs;$env:PATH"
    } else {
        throw 'npm.cmd not found. Reinstall Node.js.'
    }
}

if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Write-Host 'First run: installing frontend dependencies with npm.cmd ci...' -ForegroundColor Cyan
    Write-Host 'This can take a few minutes, but only on the first clone.' -ForegroundColor DarkGray
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) {
        throw 'npm.cmd ci failed. Close other Vite/Node terminals and retry.'
    }
}

Write-Host 'Frontend READY target: http://localhost:5173' -ForegroundColor Cyan
& npm.cmd run dev -- --port 5173 --strictPort
exit $LASTEXITCODE
