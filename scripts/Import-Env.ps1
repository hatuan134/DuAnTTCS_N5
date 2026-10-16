param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $EnvFile)) {
    throw "Missing .env: $EnvFile"
}

[System.IO.File]::ReadAllLines($EnvFile, [System.Text.Encoding]::UTF8) | ForEach-Object {
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
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
