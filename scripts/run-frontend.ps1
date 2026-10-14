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

# Free port 5173 if lingering from a previous session
try {
    $port5173 = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
    if ($port5173) {
        $pids = $port5173 | ForEach-Object { $_.OwningProcess } | Select-Object -Unique
        foreach ($pidToKill in $pids) {
            Write-Host "Port 5173 dang duoc su dung boi PID $pidToKill. Tu dong giai phong port..." -ForegroundColor Yellow
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    }
} catch {
    # Ignore any inspection failure
}

Write-Host 'Frontend READY target: http://localhost:5173' -ForegroundColor Cyan
& npm.cmd run dev -- --port 5173 --strictPort
exit $LASTEXITCODE
