#!/usr/bin/env bash
set -u

namespace=${CLOUDWATCH_NAMESPACE:-UsedMarketplace}
region=${AWS_REGION:-eu-north-1}
healthy=0

for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
  if curl --fail --silent --max-time 5 "http://127.0.0.1:$port/actuator/health" | grep -q '"status":"UP"'; then
    healthy=$((healthy + 1))
  fi
done

gateway=0
if curl --fail --silent --max-time 5 http://127.0.0.1/healthz >/dev/null; then
  gateway=1
fi

disk_used=$(df -P / | awk 'NR==2 {gsub(/%/, "", $5); print $5}')
memory_used=$(awk '/MemTotal/ {total=$2} /MemAvailable/ {available=$2} END {printf "%.1f", (total-available)*100/total}' /proc/meminfo)

logger -t used-monitor "healthy_services=$healthy gateway_healthy=$gateway disk_used_percent=$disk_used memory_used_percent=$memory_used"

if [[ ${CLOUDWATCH_ENABLED:-false} == true ]]; then
  command -v aws >/dev/null || { logger -t used-monitor 'CloudWatch enabled but aws CLI is missing'; exit 1; }
  token=$(curl --silent --show-error --fail --max-time 2 -X PUT \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' \
    http://169.254.169.254/latest/api/token) || exit 1
  instance_id=$(curl --silent --show-error --fail --max-time 2 \
    -H "X-aws-ec2-metadata-token: $token" \
    http://169.254.169.254/latest/meta-data/instance-id) || exit 1
  dimension="Name=InstanceId,Value=$instance_id"
  aws cloudwatch put-metric-data --region "$region" --namespace "$namespace" --metric-data \
    "MetricName=HealthyServices,Dimensions=[$dimension],Value=$healthy,Unit=Count" \
    "MetricName=GatewayHealthy,Dimensions=[$dimension],Value=$gateway,Unit=Count" \
    "MetricName=DiskUsedPercent,Dimensions=[$dimension],Value=$disk_used,Unit=Percent" \
    "MetricName=MemoryUsedPercent,Dimensions=[$dimension],Value=$memory_used,Unit=Percent"
fi

[[ $healthy == 9 && $gateway == 1 ]]
