#!/usr/bin/env bash
set -euo pipefail
# All nine aggregate health checks, including SMTP health in Notification.
# This is a startup/dependency check, not business-flow or Selenium evidence.
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  response=$(curl --fail --silent --show-error --max-time 10 "http://127.0.0.1:$port/actuator/health")
  python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"' <<< "$response"
done
curl --fail --silent --show-error --max-time 10 http://127.0.0.1/healthz >/dev/null
curl --fail --silent --show-error --max-time 10 http://127.0.0.1/ >/dev/null
for path in /api/internal/users/1 /actuator/health; do
  code=$(curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1$path")
  [[ "$code" == 404 ]] || { echo "Gateway failed to block $path: $code" >&2; exit 1; }
done
echo 'All service health checks and gateway smoke checks passed.'
