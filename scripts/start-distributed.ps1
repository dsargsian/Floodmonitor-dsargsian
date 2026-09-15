param([string]$Java = 'java')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $projectRoot 'target/floodmonitor-1.0.0.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Zuerst .\mvnw.cmd package ausführen.' }
$logDirectory = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force $logDirectory | Out-Null
$processes = @()
try {
    foreach ($index in 1..3) {
        $port = 8080 + $index
        $station = 'ST-00' + $index
        $processes += Start-Process -FilePath $Java -ArgumentList @('-jar', ('"' + $jar + '"'), '--monitor.mode=station', "--monitor.station-id=$station", "--server.port=$port") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logDirectory/station-$index.log" -RedirectStandardError "$logDirectory/station-$index-error.log"
    }
    $processes += Start-Process -FilePath $Java -ArgumentList @('-jar', ('"' + $jar + '"'), '--monitor.mode=central') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logDirectory/central.log" -RedirectStandardError "$logDirectory/central-error.log"
    Write-Host 'Drei Stationen auf 8081–8083, Zentrale auf http://127.0.0.1:8080. Enter beendet alle vier Prozesse.'
    Read-Host | Out-Null
} finally {
    foreach ($process in $processes) { if (!$process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
}
