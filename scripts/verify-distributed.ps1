param([string]$Java = 'java')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $projectRoot 'target/floodmonitor-1.0.0.jar'
$logs = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force $logs | Out-Null
$processes = @()
function Await-Condition([scriptblock]$Condition, [string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        try { if (& $Condition) { Write-Output "PASS: $Description"; return } } catch {}
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timeout: $Description"
}
try {
    foreach ($i in 1..3) {
        $processes += Start-Process -FilePath $Java -ArgumentList @('-jar', ('"' + $jar + '"'), '--monitor.mode=station', "--monitor.station-id=ST-00$i", "--server.port=$(18080 + $i)", '--simulation.interval=500', '--security.admin-key=local-smoke-key') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logs/smoke-station-$i.log" -RedirectStandardError "$logs/smoke-station-$i-error.log"
    }
    $processes += Start-Process -FilePath $Java -ArgumentList @('-jar', ('"' + $jar + '"'), '--monitor.mode=central', '--server.port=18080', '--simulation.interval=500', '--measurements.stale-after=PT2S', '--monitor.sources=http://127.0.0.1:18081,http://127.0.0.1:18082,http://127.0.0.1:18083') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logs/smoke-central.log" -RedirectStandardError "$logs/smoke-central-error.log"
    $base = 'http://127.0.0.1:18080/api/v1'
    Await-Condition { (Invoke-RestMethod "$base/stations/ST-003/measurements").measurements.Count -ge 36 } 'Central aggregates node history'
    foreach ($i in 1..3) {
        $node = Invoke-RestMethod "http://127.0.0.1:$(18080 + $i)/api/v1/stations"
        if ($node.stations.Count -ne 1 -or $node.stations[0].id -ne "ST-00$i") { throw 'Station process contains wrong stations' }
    }
    Write-Output 'PASS: Three JVMs each own exactly one station'
    $before = (Invoke-RestMethod "$base/stations/ST-001/measurements/latest").timestamp
    Await-Condition { (Invoke-RestMethod "$base/stations/ST-001/measurements/latest").timestamp -gt $before } 'Scheduled measurements reach central'
    Invoke-RestMethod -Method Patch 'http://127.0.0.1:18082/api/v1/stations/ST-002' -Headers @{'X-API-Key'='local-smoke-key'} -ContentType 'application/json' -Body '{"isActive":false}' | Out-Null
    Await-Condition { (Invoke-RestMethod "$base/stations/ST-002/measurements/latest").warningLevel -eq 'UNKNOWN' } 'Node deactivation reaches central'
    Invoke-RestMethod -Method Delete 'http://127.0.0.1:18082/api/v1/stations/ST-002' -Headers @{'X-API-Key'='local-smoke-key'} | Out-Null
    Await-Condition { (Invoke-RestMethod "$base/stations").stations.Count -eq 2 } 'Node deletion removes aggregate'
    Stop-Process -Id $processes[0].Id
    Await-Condition { (Invoke-RestMethod "$base/stations/ST-001/measurements/latest").warningLevel -eq 'UNKNOWN' } 'Failed node becomes UNKNOWN'
    $xml = Invoke-WebRequest "$base/stations/ST-003/measurements/latest" -Headers @{Accept='application/xml'}
    if (([xml]$xml.Content).measurement.stationId -ne 'ST-003') { throw 'XML aggregation failed' }
    Write-Output 'PASS: Remaining node still serves XML after another node fails'
} finally {
    foreach ($process in $processes) { if (!$process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
}
