param(
    [switch]$SkipInstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Write-Step([string]$Text) {
    Write-Host "`n==> $Text" -ForegroundColor Cyan
}

function Has-Command([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$machine;$user"
}

function Install-PackageIfMissing {
    param(
        [string]$Command,
        [string]$PackageId,
        [string]$Label
    )

    if (Has-Command $Command) {
        Write-Host "${Label}: đã có." -ForegroundColor Green
        return $false
    }

    if ($SkipInstall) {
        throw "$Label chưa được cài. Bỏ -SkipInstall hoặc cài thủ công."
    }

    if (-not (Has-Command 'winget')) {
        throw 'Không tìm thấy winget. Hãy cập nhật App Installer từ Microsoft Store rồi chạy lại setup.ps1.'
    }

    Write-Host "Đang cài $Label bằng winget ($PackageId)..." -ForegroundColor Yellow
    winget install --id $PackageId -e --accept-package-agreements --accept-source-agreements | Out-Host
    $installExitCode = $LASTEXITCODE
    if ($installExitCode -ne 0) {
        throw "Cài $Label thất bại."
    }

    Refresh-Path
    return $true
}


function Get-JavaMajor {
    if (-not (Has-Command 'java')) {
        return $null
    }

    # java -version writes to STDERR. On Windows PowerShell 5.1, with
    # $ErrorActionPreference='Stop', direct redirection can become NativeCommandError.
    # Run through cmd.exe so version detection is stable across PowerShell versions.
    $javaVersionLines = & $env:ComSpec /d /c 'java -version 2>&1'
    $firstLine = ($javaVersionLines | Select-Object -First 1) -join ''
    if ($firstLine -match '"(?<major>\d+)(?:\.|"|$)') {
        return [int]$Matches['major']
    }
    return $null
}

function Configure-Java17 {
    $candidates = @()
    $roots = @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java')
    )

    foreach ($rootPath in $roots) {
        if (Test-Path $rootPath) {
            $candidates += Get-ChildItem $rootPath -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like 'jdk-17*' } |
                Sort-Object Name -Descending
        }
    }

    $jdk = $candidates | Select-Object -First 1
    if ($null -eq $jdk) {
        return $false
    }

    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$($jdk.FullName)\bin;$env:Path"
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk.FullName, 'User')
    Write-Host "JAVA_HOME -> $($jdk.FullName)" -ForegroundColor Green
    return $true
}

function Ensure-Java17 {
    $major = Get-JavaMajor
    if ($major -eq 17) {
        Write-Host 'JDK 17: đã có.' -ForegroundColor Green
        return
    }

    if (-not $SkipInstall) {
        if (-not (Has-Command 'winget')) {
            throw 'Không tìm thấy winget để cài JDK 17.'
        }
        Write-Host "Java hiện tại không phải 17 (major=$major). Đang đảm bảo Temurin JDK 17..." -ForegroundColor Yellow
        winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements | Out-Host
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw 'Cài Temurin JDK 17 thất bại.'
        }
        Refresh-Path
    }

    if (-not (Configure-Java17)) {
        throw 'Đã yêu cầu JDK 17 nhưng không tìm thấy thư mục cài đặt. Hãy restart Windows rồi chạy lại setup.ps1 -SkipInstall.'
    }

    $major = Get-JavaMajor
    if ($major -ne 17) {
        throw "Không kích hoạt được Java 17 trong terminal hiện tại (major=$major)."
    }
}

function Ensure-Node {
    $ok = $false
    if (Has-Command 'node') {
        $raw = (node -v).TrimStart('v')
        $parts = $raw.Split('.')
        if ($parts.Count -gt 0) {
            $major = [int]$parts[0]
            if ($major -ge 22) {
                $ok = $true
            }
        }
    }

    if ($ok) {
        Write-Host 'Node.js 22+ : đã có.' -ForegroundColor Green
        return
    }

    if ($SkipInstall) {
        throw 'Node.js 22+ chưa sẵn sàng.'
    }

    Write-Host 'Đang cài Node.js 22 bằng winget...' -ForegroundColor Yellow
    winget install --id OpenJS.NodeJS.22 -e --accept-package-agreements --accept-source-agreements | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Không cài được package OpenJS.NodeJS.22; thử Node.js LTS.'
        winget install --id OpenJS.NodeJS.LTS -e --accept-package-agreements --accept-source-agreements | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw 'Cài Node.js thất bại.'
        }
    }
    Refresh-Path
}

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

Write-Host 'DuAnTTCS_N5 - Setup môi trường local' -ForegroundColor White
Write-Host 'Git/JDK/Node/VS Code/Docker dùng winget; PostgreSQL chạy bằng Docker Compose.' -ForegroundColor DarkGray

Write-Step '1. Kiểm tra/cài công cụ'
$installedSomething = $false
$installedSomething = (Install-PackageIfMissing -Command 'git' -PackageId 'Git.Git' -Label 'Git') -or $installedSomething
Ensure-Java17
Ensure-Node
$installedSomething = (Install-PackageIfMissing -Command 'code' -PackageId 'Microsoft.VisualStudioCode' -Label 'VS Code') -or $installedSomething
$installedSomething = (Install-PackageIfMissing -Command 'docker' -PackageId 'Docker.DockerDesktop' -Label 'Docker Desktop') -or $installedSomething

Refresh-Path
Configure-Java17 | Out-Null

Write-Step '2. Kiểm tra phiên bản'
if (Has-Command 'git') { git --version }
if (Has-Command 'java') { & $env:ComSpec /d /c 'java -version 2>&1' | Out-Host }
if (Has-Command 'javac') { & $env:ComSpec /d /c 'javac -version 2>&1' | Out-Host }
if (Has-Command 'node') { node -v }
if (Has-Command 'npm') { npm -v }
if (Has-Command 'docker') { docker --version }
Set-Location (Join-Path $Root 'backend')
.\mvnw.cmd -version
Set-Location $Root

Write-Step '3. Tạo .env local nếu chưa có'
$envPath = Join-Path $Root '.env'
if (-not (Test-Path $envPath)) {
    $dbPassword = 'Db_' + ([Guid]::NewGuid().ToString('N').Substring(0, 20))
    $jwtSecret = New-RandomBase64 32

    $envContent = @"
# Tự sinh bởi setup.ps1 - KHÔNG COMMIT FILE NÀY
DB_HOST=127.0.0.1
DB_PORT=5433
DB_NAME=library_management
DB_USERNAME=postgres
DB_PASSWORD=$dbPassword
SERVER_PORT=8080
JWT_SECRET_BASE64=$jwtSecret
BOOTSTRAP_ADMIN_EMAIL=admin@libra.edu.vn
BOOTSTRAP_ADMIN_PASSWORD=Admin123
BOOTSTRAP_ADMIN_FULL_NAME=Quản trị hệ thống
"@
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($envPath, $envContent, $utf8NoBom)

    Write-Host 'Đã tạo .env với DB password và JWT secret ngẫu nhiên.' -ForegroundColor Green
}
else {
    Write-Host '.env đã tồn tại - giữ nguyên, không ghi đè.' -ForegroundColor Green
}

& (Join-Path $Root 'scripts\Import-Env.ps1')

Write-Step '4. Chuẩn bị Docker Desktop/PostgreSQL'
if (-not (Has-Command 'docker')) {
    Write-Warning 'Docker Desktop đã được yêu cầu cài nhưng Docker CLI chưa xuất hiện trong PATH. Hãy restart Windows rồi chạy lại .\setup.ps1 -SkipInstall.'
    exit 2
}

$dockerReady = $false
if (Has-Command 'docker') {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        $dockerReady = $true
    }
}

if (-not $dockerReady) {
    $dockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path $dockerExe) {
        Write-Host 'Đang mở Docker Desktop...' -ForegroundColor Yellow
        Start-Process $dockerExe | Out-Null
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 2
            docker info *> $null
            if ($LASTEXITCODE -eq 0) {
                $dockerReady = $true
                break
            }
        }
    }
}

if (-not $dockerReady) {
    Write-Warning 'Docker Desktop chưa sẵn sàng. Nếu vừa cài lần đầu, hãy restart Windows/mở Docker Desktop, sau đó chạy lại .\setup.ps1 -SkipInstall.'
    exit 2
}

& (Join-Path $Root 'scripts\start-db.ps1')

Write-Step '5. Cài dependency Frontend'
Set-Location (Join-Path $Root 'frontend')
npm ci
if ($LASTEXITCODE -ne 0) {
    throw 'npm ci thất bại.'
}

if (-not $SkipBuild) {
    Write-Step '6. Compile Backend'
    & (Join-Path $Root 'scripts\Import-Env.ps1')
    Set-Location (Join-Path $Root 'backend')
    .\mvnw.cmd clean compile
    if ($LASTEXITCODE -ne 0) {
        throw 'Backend compile thất bại.'
    }

    Write-Step '7. Build Frontend'
    Set-Location (Join-Path $Root 'frontend')
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw 'Frontend build thất bại.'
    }
}

Set-Location $Root
Write-Host "`nSETUP HOÀN TẤT" -ForegroundColor Green
Write-Host 'Chạy Backend : .\scripts\run-backend.ps1'
Write-Host 'Chạy Frontend: .\scripts\run-frontend.ps1'
Write-Host 'DB status    : docker compose ps'
Write-Host 'Dừng DB      : docker compose stop'
Write-Host 'Lưu ý: không dùng docker compose down -v nếu không muốn xóa sạch dữ liệu local.' -ForegroundColor Yellow
