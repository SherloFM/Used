#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Run with sudo' >&2; exit 1; }
schema=$(cd "$(dirname "$0")" && pwd)/schema.sql
[[ -s "$schema" ]] || { echo 'Missing reviewed schema.sql baseline' >&2; exit 1; }
compose=(/usr/local/bin/docker-compose --env-file /etc/used/used.env -f /etc/used/compose.yaml)
psql=("${compose[@]}" exec -T postgres psql -U used -d used -v ON_ERROR_STOP=1)
count=$("${psql[@]}" -Atc "SELECT count(*) FROM pg_tables WHERE schemaname='public'")
[[ "$count" == 0 ]] || { echo 'Database is not empty; initialization refused. Do not reinitialize during deployment.' >&2; exit 1; }
"${psql[@]}" --single-transaction < "$schema"
"${psql[@]}" -c '\dt'
echo 'SCHEMA_INITIALIZED: empty database; no demo users or personal data imported.'
