#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -fsS "${BASE_URL}/actuator/health" >/dev/null

echo "== http.server.requests metrics =="
curl -fsS "${BASE_URL}/actuator/metrics/http.server.requests"
echo
echo
echo "== hikari connections active =="
curl -fsS "${BASE_URL}/actuator/metrics/hikaricp.connections.active"
echo
echo
echo "== hikari connections pending =="
curl -fsS "${BASE_URL}/actuator/metrics/hikaricp.connections.pending"
echo
echo
echo "== prometheus sample =="
curl -fsS "${BASE_URL}/actuator/prometheus" | rg "^(http_server_requests_seconds_count|hikaricp_connections_active|jvm_threads_live_threads)" || true
