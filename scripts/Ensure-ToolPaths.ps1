# scripts/Ensure-ToolPaths.ps1
# Synchronizes environment variables (PATH, JAVA_HOME) from Windows Registry
# and discovers standard installations of JDK 17, Node.js, and Docker Desktop.

$ErrorActionPreference = 'Stop'

function Refresh-ToolEnvironment {
    $pathsToAdd = New-Object System.Collections.Generic.List[string]

    $addPath = {
        param([string]$dir)
        if (-not [string]::IsNullOrWhiteSpace($dir)) {
            $cleaned = $dir.Trim('"', "'", ' ')
            if ((Test-Path -LiteralPath $cleaned) -and (-not $pathsToAdd.Contains($cleaned))) {
                $pathsToAdd.Add($cleaned)
            }
        }
    }

    # 1. Look for JDK 17 installation
    $detectedJdk17Home = $null
    $jdkSearchList = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) { $jdkSearchList.Add($env:JAVA_HOME) }
    $regJavaHomeMachine = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ($regJavaHomeMachine) { $jdkSearchList.Add($regJavaHomeMachine) }
    $regJavaHomeUser = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    if ($regJavaHomeUser) { $jdkSearchList.Add($regJavaHomeUser) }

    # Common JDK 17 directories
    $commonJdkPatterns = @(
        'C:\Program Files\Eclipse Adoptium\jdk-17*',
        'C:\Program Files\Java\jdk-17*',
        'C:\Program Files\Microsoft\jdk-17*',
        'C:\Program Files\Amazon Corretto\jdk17*',
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-17*"
    )
    foreach ($pattern in $commonJdkPatterns) {
        $resolved = Resolve-Path $pattern -ErrorAction SilentlyContinue
        if ($resolved) {
            foreach ($r in $resolved) { $jdkSearchList.Add($r.Path) }
        }
    }

   foreach ($jdkHome in $jdkSearchList) {
        if (-not $home) { continue }
        $binDir = Join-Path $home 'bin'
        $javaExe = Join-Path $binDir 'java.exe'
        if (Test-Path -LiteralPath $javaExe) {
            $ver = & $javaExe -version 2>&1 | Out-String
            if ($ver -match '"17[\.\d_]*') {
                $detectedJdk17Home = $home
                & $addPath $binDir
                break
            }
        }
    }

    if ($detectedJdk17Home) {
        $env:JAVA_HOME = $detectedJdk17Home
    }

    # 2. Look for Node.js and npm
    $nodeDirs = @(
        'C:\Program Files\nodejs',
        (Join-Path $env:APPDATA 'npm'),
        (Join-Path $env:LOCALAPPDATA 'Programs\nodejs')
    )
    foreach ($nd in $nodeDirs) {
        & $addPath $nd
    }

    # 3. Look for Docker Desktop CLI
    $dockerDirs = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'),
        'C:\Program Files\Docker\Docker\resources\bin',
        'C:\Program Files\Docker\Docker\resources'
    )
    foreach ($dd in $dockerDirs) {
        & $addPath $dd
    }

    # 4. Pull Machine and User PATH from Registry
    $regMachine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    if ($regMachine) {
        foreach ($p in ($regMachine -split ';')) { & $addPath $p }
    }
    $regUser = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($regUser) {
        foreach ($p in ($regUser -split ';')) { & $addPath $p }
    }

    # 5. Append current process PATH to avoid losing anything custom
    if ($env:Path) {
        foreach ($p in ($env:Path -split ';')) { & $addPath $p }
    }

    $env:Path = ($pathsToAdd -join ';')
}

Refresh-ToolEnvironment
