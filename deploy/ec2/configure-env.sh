#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Run with: sudo bash deploy/ec2/configure-env.sh' >&2; exit 1; }
env_file=/etc/used/used.env
[[ -f "$env_file" ]] || { echo "Missing $env_file; run install-host.sh first." >&2; exit 1; }
if ! grep -q 'REPLACE_' "$env_file"; then
  echo "$env_file has no placeholders left; refusing to overwrite an existing configuration." >&2
  exit 1
fi

# Discover the public address from instance metadata (IMDSv2).
token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
public_ip=$(curl -fsS -H "X-aws-ec2-metadata-token: $token" \
  http://169.254.169.254/latest/meta-data/public-ipv4)
[[ "$public_ip" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || {
  echo "Could not determine a public IPv4 address (got: ${public_ip:-none})." >&2
  exit 1
}

echo "Public address: $public_ip"
echo 'Notification health checks the mail server, so working SMTP details are required.'
# Non-interactive mode: supply PF_SMTP_HOST, PF_SMTP_USER and PF_SMTP_PASS so the
# script can run as one command when hidden password input is inconvenient.
if [[ -n "${PF_SMTP_HOST:-}" && -n "${PF_SMTP_USER:-}" && -n "${PF_SMTP_PASS:-}" ]]; then
  smtp_host=$PF_SMTP_HOST
  smtp_port=${PF_SMTP_PORT:-587}
  smtp_user=$PF_SMTP_USER
  smtp_pass=$PF_SMTP_PASS
  echo 'Using SMTP settings supplied through the environment.'
else
  read -rp 'SMTP host (for example smtp.gmail.com): ' smtp_host
  read -rp 'SMTP port [587]: ' smtp_port
  smtp_port=${smtp_port:-587}
  read -rp 'SMTP username (usually the full email address): ' smtp_user
  read -rsp 'SMTP password or app password: ' smtp_pass
  echo
fi
[[ -n "$smtp_host" && -n "$smtp_user" && -n "$smtp_pass" && "$smtp_port" =~ ^[0-9]+$ ]] || {
  echo 'SMTP host, username, password and a numeric port are required.' >&2
  exit 1
}

echo 'Generating secrets and the Kafka cluster id...'
export PF_DB_PASSWORD PF_JWT_SECRET PF_INTERNAL_TOKEN PF_RABBIT_PASSWORD
PF_DB_PASSWORD=$(openssl rand -hex 24)
PF_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
PF_INTERNAL_TOKEN=$(openssl rand -hex 32)
PF_RABBIT_PASSWORD=$(openssl rand -hex 24)
export PF_KAFKA_CLUSTER_ID
PF_KAFKA_CLUSTER_ID=$(docker run --rm apache/kafka:4.1.0 \
  /opt/kafka/bin/kafka-storage.sh random-uuid)
[[ "$PF_KAFKA_CLUSTER_ID" =~ ^[A-Za-z0-9_-]{20,}$ ]] || {
  echo "Unexpected Kafka cluster id: $PF_KAFKA_CLUSTER_ID" >&2
  exit 1
}

export PF_PUBLIC_IP="$public_ip" PF_SMTP_HOST="$smtp_host" PF_SMTP_PORT="$smtp_port"
export PF_SMTP_USER="$smtp_user" PF_SMTP_PASS="$smtp_pass"
python3 - "$env_file" <<'PY'
import os, pathlib, sys
path = pathlib.Path(sys.argv[1])
values = {
    'DB_PASSWORD': os.environ['PF_DB_PASSWORD'],
    'JWT_SECRET': os.environ['PF_JWT_SECRET'],
    'INTERNAL_API_TOKEN': os.environ['PF_INTERNAL_TOKEN'],
    'RABBITMQ_PASSWORD': os.environ['PF_RABBIT_PASSWORD'],
    'KAFKA_CLUSTER_ID': os.environ['PF_KAFKA_CLUSTER_ID'],
    'FRONTEND_ORIGINS': f"http://{os.environ['PF_PUBLIC_IP']}",
    'SPRING_MAIL_HOST': os.environ['PF_SMTP_HOST'],
    'SPRING_MAIL_PORT': os.environ['PF_SMTP_PORT'],
    'SPRING_MAIL_USERNAME': os.environ['PF_SMTP_USER'],
    'SPRING_MAIL_PASSWORD': os.environ['PF_SMTP_PASS'],
}
lines = path.read_text().splitlines()
seen = set()
out = []
for line in lines:
    if '=' in line and not line.lstrip().startswith('#'):
        key = line.split('=', 1)[0]
        if key in values:
            out.append(f'{key}={values[key]}')
            seen.add(key)
            continue
    out.append(line)
missing = set(values) - seen
if missing:
    sys.exit(f'Environment template is missing: {sorted(missing)}')
text = '\n'.join(out) + '\n'
if 'REPLACE_' in text:
    sys.exit('Placeholders remain in the environment file')
tmp = path.with_suffix('.new')
tmp.write_text(text)
tmp.chmod(0o600)
tmp.replace(path)
PY

unset PF_DB_PASSWORD PF_JWT_SECRET PF_INTERNAL_TOKEN PF_RABBIT_PASSWORD \
  PF_KAFKA_CLUSTER_ID PF_SMTP_PASS
echo 'Environment file written to /etc/used/used.env (mode 0600).'
echo "Website: http://$public_ip  Jenkins: http://$public_ip:8090"
echo 'Next: sudo bash deploy/ec2/start-infra.sh'
