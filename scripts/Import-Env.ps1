param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env')
)

if (-not (Test-Path $EnvFile)) {
    throw "Không tìm thấy file .env: $EnvFile. Hãy chạy .\setup.ps1 trước hoặc copy .env.example thành .env."
}

Get-Content -Path $EnvFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()

    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) {
        return
    }

    $name = $parts[0].Trim()
    $value = $parts[1].Trim()

    if ($value.Length -ge 2) {
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
