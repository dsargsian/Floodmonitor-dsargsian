#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
test -f target/floodmonitor-1.0.0.jar || { echo 'First run ./mvnw package'; exit 1; }
mkdir -p logs
pids=''
cleanup() { for pid in $pids; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM
for i in 1 2 3; do
  java -jar target/floodmonitor-1.0.0.jar --monitor.mode=station --monitor.station-id="ST-00$i" --server.port="$((8080+i))" >"logs/station-$i.log" 2>&1 &
  pids="$pids $!"
done
java -jar target/floodmonitor-1.0.0.jar --monitor.mode=central >logs/central.log 2>&1 &
pids="$pids $!"
echo 'Central: http://127.0.0.1:8080 — Enter stops all four processes.'
read -r answer
