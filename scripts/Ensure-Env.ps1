$ErrorActionPreference = 'Stop'

$Root = Split-Path $PSScriptRoot -Parent
$EnvPath = Join-Path $Root '.env'

function New-RandomBase64([int]$BytesCount) {
    $bytes = New-Object byte[] $BytesCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $rng.Dispose()
    }
}

function New-DbPassword {
    return 'Db_' + ([Guid]::NewGuid().ToString('N').Substring(0, 24))
}

function Read-EnvMap([string]$Path) {
    $map = [ordered]@{}
    if (-not (Test-Path $Path)) {
        return $map
    }

    [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8) | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) {
            return
        }

        $parts = $line -split '=', 2
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        if ($name) {
            $map[$name] = $value
        }
    }

    return $map
}

function Test-JwtSecret([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    try {
        $decoded = [Convert]::FromBase64String($Value.Trim())
        return $decoded.Length -ge 32
    }
    catch {
        return $false
    }
}

$envMap = Read-EnvMap $EnvPath
$changed = $false

$defaults = [ordered]@{
    DB_HOST = '127.0.0.1'
    DB_PORT = '5433'
    DB_NAME = 'library_management'
    DB_USERNAME = 'postgres'
    SERVER_PORT = '8080'
    BOOTSTRAP_ADMIN_EMAIL = 'admin@libra.edu.vn'
    BOOTSTRAP_ADMIN_PASSWORD = 'Admin123'
    BOOTSTRAP_ADMIN_FULL_NAME = 'Quan tri he thong'
}

foreach ($entry in $defaults.GetEnumerator()) {
    if (-not $envMap.Contains($entry.Key) -or [string]::IsNullOrWhiteSpace([string]$envMap[$entry.Key])) {
        $envMap[$entry.Key] = $entry.Value
        $changed = $true
    }
}

$dbPassword = if ($envMap.Contains('DB_PASSWORD')) { [string]$envMap['DB_PASSWORD'] } else { '' }
if ([string]::IsNullOrWhiteSpace($dbPassword) -or $dbPassword -match '^CHANGE_ME') {
    $envMap['DB_PASSWORD'] = New-DbPassword
    $changed = $true
}

$jwtSecret = if ($envMap.Contains('JWT_SECRET_BASE64')) { [string]$envMap['JWT_SECRET_BASE64'] } else { '' }
if (-not (Test-JwtSecret $jwtSecret)) {
    $envMap['JWT_SECRET_BASE64'] = New-RandomBase64 32
    $changed = $true
    Write-Host 'JWT secret missing/invalid -> generated a new local secret.' -ForegroundColor Yellow
}

# Keep the file deterministic and UTF-8 without BOM. Do not print secrets.
$keys = @(
    'DB_HOST',
    'DB_PORT',
    'DB_NAME',
    'DB_USERNAME',
    'DB_PASSWORD',
    'SERVER_PORT',
    'JWT_SECRET_BASE64',
    'BOOTSTRAP_ADMIN_EMAIL',
    'BOOTSTRAP_ADMIN_PASSWORD',
    'BOOTSTRAP_ADMIN_FULL_NAME'
)

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# Local development only - DO NOT COMMIT THIS FILE')
foreach ($key in $keys) {
    $lines.Add("$key=$($envMap[$key])")
}

# Preserve optional/custom values (for example SMTP settings used by S1-02/S1-07)
# instead of silently deleting them every time RUN_ALL.cmd repairs .env.
foreach ($entry in $envMap.GetEnumerator()) {
    if ($keys -notcontains $entry.Key) {
        $lines.Add("$($entry.Key)=$($entry.Value)")
    }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($EnvPath, $lines, $utf8NoBom)

if ($changed -or -not (Test-Path $EnvPath)) {
    Write-Host '.env created/repaired.' -ForegroundColor Green
}
else {
    Write-Host '.env valid.' -ForegroundColor Green
}
