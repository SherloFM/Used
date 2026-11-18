#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Run with sudo bash deploy/ec2/install-host.sh' >&2; exit 1; }
source /etc/os-release
[[ "$ID" == amzn && "$VERSION_ID" == 2023 ]] || { echo 'Requires Amazon Linux 2023' >&2; exit 1; }
repo=$(cd "$(dirname "$0")/../.." && pwd)

# Earlier manual setup added these invalid repositories. Preserve as backups.
for name in erlang rabbitmq; do
  if [[ -f /etc/yum.repos.d/$name.repo ]]; then
    mv "/etc/yum.repos.d/$name.repo" "/etc/yum.repos.d/$name.repo.disabled.$(date +%s)"
  fi
done
dnf install -y git docker nginx python3 fontconfig java-17-amazon-corretto-devel java-21-amazon-corretto-devel
curl -fsSL https://pkg.jenkins.io/rpm-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo
rpm --import https://pkg.jenkins.io/rpm-stable/jenkins.io-2026.key
dnf install -y jenkins
# Testcontainers starts disposable PostgreSQL through the host Docker socket.
# Docker group membership is root-equivalent, but this is a dedicated build host.
usermod -aG docker jenkins
# Compose was installed during the user's EC2 setup. Fail rather than silently
# changing versions or fetching an unverified executable on every install.
/usr/local/bin/docker-compose version

id used >/dev/null 2>&1 || useradd --system --home-dir /nonexistent --shell /sbin/nologin used
install -d -m 755 /opt/used
chown jenkins:jenkins /opt/used
install -d -o jenkins -g jenkins -m 755 /opt/used/releases
install -d -m 700 /etc/used
install -m 644 "$repo/deploy/ec2/compose.yaml" /etc/used/compose.yaml
if [[ ! -e /etc/used/used.env ]]; then
  install -m 600 "$repo/deploy/ec2/.env.example" /etc/used/used.env
fi
java17=$(rpm -ql java-17-amazon-corretto-devel | grep '/bin/javac$' | head -n1)
java21=$(rpm -ql java-21-amazon-corretto-devel | grep '/bin/javac$' | head -n1)
[[ -x "$java17" && -x "$java21" ]] || { echo 'Could not locate Corretto JDKs' >&2; exit 1; }
ln -sfn "$(dirname "$(dirname "$java17")")" /opt/used/java17
install -d /etc/systemd/system/jenkins.service.d
cat > /etc/systemd/system/jenkins.service.d/used.conf <<EOF
[Service]
Environment="JENKINS_PORT=8090"
Environment="JENKINS_JAVA_CMD=$(dirname "$java21")/java"
Environment="JAVA_OPTS=-Djava.awt.headless=true -Xms128m -Xmx512m"
EOF
install -m 644 "$repo/deploy/ec2/used@.service" /etc/systemd/system/used@.service
install -d -m 755 /usr/local/libexec
install -m 755 "$repo/deploy/ec2/monitor-health.sh" /usr/local/libexec/used-monitor-health
install -m 644 "$repo/deploy/ec2/used-monitor.service" /etc/systemd/system/used-monitor.service
install -m 644 "$repo/deploy/ec2/used-monitor.timer" /etc/systemd/system/used-monitor.timer
if [[ ! -e /etc/used/monitoring.env ]]; then
  install -m 600 "$repo/deploy/ec2/monitoring.env.example" /etc/used/monitoring.env
fi

# Preserve the canonical route map; only adapt listen/upstream addresses.
# The generator also keeps an existing TLS listener, so re-running this script
# on a site that already has HTTPS does not downgrade it to plain HTTP.
python3 "$repo/deploy/ec2/render-nginx.py" "$repo/gateway/nginx.conf" /etc/nginx/used.conf
install -d /etc/systemd/system/nginx.service.d
cat > /etc/systemd/system/nginx.service.d/used.conf <<'EOF'
[Service]
ExecStartPre=
ExecStartPre=/usr/sbin/nginx -t -c /etc/nginx/used.conf
ExecStart=
ExecStart=/usr/sbin/nginx -c /etc/nginx/used.conf
ExecReload=
ExecReload=/usr/sbin/nginx -s reload -c /etc/nginx/used.conf
EOF
# Jenkins can restart only the application units, not run arbitrary root commands.
sudoers=$(mktemp)
for service in accounts chat marketplace favorites notification moderation audit media frontend; do
  printf 'jenkins ALL=(root) NOPASSWD: /usr/bin/systemctl restart used@%s-service.service, /usr/bin/systemctl stop used@%s-service.service\n' "$service" "$service" >> "$sudoers"
done
visudo -cf "$sudoers"
install -m 440 "$sudoers" /etc/sudoers.d/used-jenkins
rm "$sudoers"
if command -v getenforce >/dev/null && [[ $(getenforce) == Enforcing ]]; then
  setsebool -P httpd_can_network_connect 1
fi
/usr/sbin/nginx -t -c /etc/nginx/used.conf
systemctl daemon-reload
systemctl enable --now docker
systemctl enable jenkins nginx
systemctl enable --now used-monitor.timer
systemctl restart jenkins nginx
echo 'HOST_READY: Jenkins :8090, gateway :80. Configure /etc/used/used.env next.'
