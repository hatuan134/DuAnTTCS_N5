$ErrorActionPreference = 'Stop'

$Root = Split-Path $PSScriptRoot -Parent
$EnvPath = Join-Path $Root '.env'
$EnsureEnv = Join-Path $PSScriptRoot 'Ensure-Env.ps1'

if (Test-Path $EnsureEnv) {
    & $EnsureEnv | Out-Host
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

        if ($name) {
            $map[$name] = $value
        }
    }

    return $map
}

function Write-EnvMap([string]$Path, $Values) {
    $preferredOrder = @(
        'DB_HOST',
        'DB_PORT',
        'DB_NAME',
        'DB_USERNAME',
        'DB_PASSWORD',
        'SERVER_PORT',
        'JWT_SECRET_BASE64',
        'BOOTSTRAP_ADMIN_EMAIL',
        'BOOTSTRAP_ADMIN_PASSWORD',
        'BOOTSTRAP_ADMIN_FULL_NAME',
        'FRONTEND_URL',
        'MAIL_HOST',
        'MAIL_PORT',
        'MAIL_USERNAME',
        'MAIL_PASSWORD',
        'MAIL_FROM'
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('# Local development only - DO NOT COMMIT THIS FILE')

    foreach ($key in $preferredOrder) {
        if ($Values.Contains($key)) {
            $lines.Add("$key=$($Values[$key])")
        }
    }

    foreach ($entry in $Values.GetEnumerator()) {
        if ($preferredOrder -notcontains $entry.Key) {
            $lines.Add("$($entry.Key)=$($entry.Value)")
        }
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
}

$values = Read-EnvMap $EnvPath

Write-Host ''
Write-Host '=== LIBRA EMAIL SETUP (Gmail SMTP) ===' -ForegroundColor Cyan
Write-Host 'Use a Gmail App Password, NOT your normal Gmail password.' -ForegroundColor Yellow
Write-Host ''

$currentUser = if ($values.Contains('MAIL_USERNAME')) { [string]$values['MAIL_USERNAME'] } else { '' }
$emailPrompt = if ($currentUser) { "Gmail address [$currentUser]" } else { 'Gmail address' }
$email = Read-Host $emailPrompt
if ([string]::IsNullOrWhiteSpace($email)) {
    $email = $currentUser
}
if ([string]::IsNullOrWhiteSpace($email)) {
    throw 'MAIL_USERNAME cannot be empty.'
}

$securePassword = Read-Host 'Gmail App Password (input is hidden)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}

if ([string]::IsNullOrWhiteSpace($appPassword)) {
    throw 'MAIL_PASSWORD cannot be empty.'
}

$values['FRONTEND_URL'] = 'http://localhost:5173'
$values['MAIL_HOST'] = 'smtp.gmail.com'
$values['MAIL_PORT'] = '587'
$values['MAIL_USERNAME'] = $email.Trim()
$values['MAIL_PASSWORD'] = ($appPassword -replace '\s', '')
$values['MAIL_FROM'] = $email.Trim()

Write-EnvMap $EnvPath $values

Write-Host ''
Write-Host 'Email settings saved to local .env.' -ForegroundColor Green
Write-Host 'The .env file is ignored by Git, so the App Password will not be committed.' -ForegroundColor Green
