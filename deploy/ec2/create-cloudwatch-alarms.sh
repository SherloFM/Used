#!/usr/bin/env bash
set -euo pipefail

region=${AWS_REGION:-eu-north-1}
namespace=${CLOUDWATCH_NAMESPACE:-UsedMarketplace}
topic=${SNS_TOPIC_ARN:-}

command -v aws >/dev/null || { echo 'AWS CLI is required.' >&2; exit 1; }
token=$(curl --silent --show-error --fail --max-time 2 -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' \
  http://169.254.169.254/latest/api/token)
instance_id=$(curl --silent --show-error --fail --max-time 2 \
  -H "X-aws-ec2-metadata-token: $token" \
  http://169.254.169.254/latest/meta-data/instance-id)

actions=()
if [[ -n $topic ]]; then
  actions=(--alarm-actions "$topic" --ok-actions "$topic")
fi

alarm() {
  local name=$1 metric=$2 statistic=$3 comparison=$4 threshold=$5 description=$6
  aws cloudwatch put-metric-alarm --region "$region" \
    --alarm-name "used-$instance_id-$name" \
    --alarm-description "$description" \
    --namespace "$namespace" --metric-name "$metric" \
    --dimensions "Name=InstanceId,Value=$instance_id" \
    --statistic "$statistic" --period 60 --evaluation-periods 2 --datapoints-to-alarm 2 \
    --comparison-operator "$comparison" --threshold "$threshold" \
    --treat-missing-data breaching "${actions[@]}"
}

alarm services HealthyServices Minimum LessThanThreshold 9 'One or more Used services are unhealthy.'
alarm gateway GatewayHealthy Minimum LessThanThreshold 1 'The Used HTTPS gateway is unhealthy.'
alarm disk DiskUsedPercent Maximum GreaterThanThreshold 85 'The Used EC2 root filesystem is above 85%.'
alarm memory MemoryUsedPercent Maximum GreaterThanThreshold 90 'The Used EC2 host memory is above 90%.'

echo "CLOUDWATCH_ALARMS_READY instance=$instance_id namespace=$namespace"
