$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$port = 8081
$jarPath = Join-Path $root "target\login-interface-package-0.0.1-SNAPSHOT.jar"
$runDir = Join-Path $root "run"
$logDir = Join-Path $root "logs"
$pidFile = Join-Path $runDir "app.pid"
$outLog = Join-Path $logDir "app.log"
$errLog = Join-Path $logDir "app-error.log"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-RunningProcess {
    param([string]$PidFilePath)

    if (-not (Test-Path -LiteralPath $PidFilePath)) {
        return $null
    }

    $rawPid = (Get-Content -LiteralPath $PidFilePath -Raw).Trim()
    if (-not $rawPid) {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $null
    }

    try {
        $pidValue = [int]$rawPid
    } catch {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $null
    }

    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $null
    }

    return $process
}

function Get-PortProcessId {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($connection) {
        return $connection.OwningProcess
    }

    return $null
}

$existingProcess = Get-RunningProcess -PidFilePath $pidFile
if ($existingProcess) {
    Write-Host "Project already running. PID=$($existingProcess.Id)"
    Write-Host "Open: http://localhost:$port"
    Start-Process "http://localhost:$port" | Out-Null
    exit 0
}

$portPid = Get-PortProcessId -Port $port
if ($portPid) {
    Write-Host "Port $port is already in use by PID=$portPid."
    Write-Host "Run stop script first if this is the old app process."
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java was not found. Please install JDK 21 and add it to PATH."
    exit 1
}

Push-Location $root
try {
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        Write-Host "Building latest code..."
        & mvn "-DskipTests" "package"
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed."
        }
    }
    else {
        if (-not (Test-Path -LiteralPath $jarPath)) {
            throw "Maven was not found and the jar file does not exist."
        }

        Write-Host "Maven not found. Starting existing jar."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Host "Jar file not found: $jarPath"
    exit 1
}

Write-Host "Starting project in background..."
$process = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jarPath `
    -WorkingDirectory $root `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -PassThru

Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii
Start-Sleep -Seconds 3

if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "Start failed. Check logs:"
    Write-Host $outLog
    Write-Host $errLog
    exit 1
}

Write-Host "Project started."
Write-Host "Open: http://localhost:$port"
Write-Host "Log: $outLog"
Start-Process "http://localhost:$port" | Out-Null
exit 0
