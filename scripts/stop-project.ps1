$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$port = 8081
$runDir = Join-Path $root "run"
$pidFile = Join-Path $runDir "app.pid"

function Get-PortProcessId {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($connection) {
        return $connection.OwningProcess
    }

    return $null
}

if (Test-Path -LiteralPath $pidFile) {
    $rawPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    if ($rawPid) {
        try {
            $pidValue = [int]$rawPid
            $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($process) {
                Write-Host "Stopping PID=$pidValue ..."
                Stop-Process -Id $pidValue -Force
                Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
                Write-Host "Project stopped."
                exit 0
            }
        }
        catch {
        }
    }

    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

$portPid = Get-PortProcessId -Port $port
if ($portPid) {
    Write-Host "PID file missing. Stopping process on port ${port}: PID=$portPid ..."
    Stop-Process -Id $portPid -Force
    Write-Host "Project stopped."
    exit 0
}

Write-Host "No running project process was found."
exit 0
