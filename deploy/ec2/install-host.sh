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

# Preserve the canonical route map; only adapt listen/upstream addresses.
python3 - "$repo/gateway/nginx.conf" <<'PY'
import pathlib, re, sys
text = pathlib.Path(sys.argv[1]).read_text()
text = re.sub(r'^resolver .*;\n', '', text, flags=re.M)
text = text.replace('listen 8080;', 'listen 80 default_server;')
text = re.sub(r'http://[a-z]+-service:(808[0-8])', r'http://127.0.0.1:\1', text)
# This is a complete config, not an http fragment. Use a separate root config
# to avoid the distribution default server competing for the same address.
pathlib.Path('/etc/nginx/used.conf').write_text(
    'events {}\nhttp {\ninclude /etc/nginx/mime.types;\n' + text + '\n}\n')
PY
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
systemctl restart jenkins nginx
echo 'HOST_READY: Jenkins :8090, gateway :80. Configure /etc/used/used.env next.'
