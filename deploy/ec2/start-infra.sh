#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Run with sudo' >&2; exit 1; }
if grep -q 'REPLACE_' /etc/used/used.env; then
  echo 'Complete every placeholder in /etc/used/used.env first.' >&2
  exit 1
fi
compose=(/usr/local/bin/docker-compose --env-file /etc/used/used.env -f /etc/used/compose.yaml)
"${compose[@]}" up -d --wait --wait-timeout 300
for topic in payment-requests payment-requests.DLT; do
  "${compose[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:19092 --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 1
done
"${compose[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --describe
