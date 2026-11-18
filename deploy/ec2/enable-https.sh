#!/usr/bin/env bash
set -euo pipefail
# Issue a trusted certificate and switch the site to HTTPS.
#
#   sudo bash deploy/ec2/enable-https.sh used.example.com you@example.com
#
# Prerequisites, all outside this script: the domain's A record points at this
# instance's Elastic IP, and security groups still allow 80 (issuance/renewal)
# and 443. Proxy/CDN mode must be DNS-only or "Always Use HTTPS" compatible.
#
# Nothing here touches the database. The site keeps serving plain HTTP until the
# certificate exists, so a failed issuance leaves a working deployment behind.

[[ $EUID == 0 ]] || { echo 'Run with: sudo bash deploy/ec2/enable-https.sh DOMAIN EMAIL' >&2; exit 1; }
domain=${1:?Usage: enable-https.sh DOMAIN EMAIL}
email=${2:?Usage: enable-https.sh DOMAIN EMAIL}
[[ "$domain" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || {
  echo "Not a bare lowercase hostname: $domain" >&2; exit 1; }
[[ "$email" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || {
  echo "Not an email address: $email" >&2; exit 1; }

repo=$(cd "$(dirname "$0")/../.." && pwd)
state=/etc/used/https.env
acme_root=/var/www/certbot
env_file=/etc/used/used.env
services=(accounts chat marketplace favorites notification moderation audit media frontend)

install_certbot() {
  if command -v certbot >/dev/null; then
    command -v certbot; return
  fi
  # Diagnostics go to stderr: stdout carries only the executable path.
  echo 'certbot not found; trying the distribution package.' >&2
  if dnf install -y certbot >/dev/null 2>&1 && command -v certbot >/dev/null; then
    command -v certbot; return
  fi
  # Amazon Linux 2023 does not always ship certbot. Use an isolated virtualenv
  # rather than touching the system Python.
  echo 'Distribution package unavailable; installing certbot in /opt/certbot.' >&2
  dnf install -y python3 python3-pip >/dev/null >&2
  python3 -m venv /opt/certbot >&2
  /opt/certbot/bin/pip install --quiet --upgrade pip >&2
  /opt/certbot/bin/pip install --quiet certbot >&2
  ln -sfn /opt/certbot/bin/certbot /usr/local/bin/certbot
  command -v certbot
}

acquire() {
  local certbot=$1
  "$certbot" certonly --webroot --webroot-path "$acme_root" \
    --domain "$domain" --email "$email" --agree-tos --no-eff-email \
    --non-interactive --keep-until-expiring
}

render() {
  python3 "$repo/deploy/ec2/render-nginx.py" "$repo/gateway/nginx.conf" /etc/nginx/used.conf
  /usr/sbin/nginx -t -c /etc/nginx/used.conf
  systemctl reload nginx
}

install -d -m 755 "$acme_root"
if command -v getenforce >/dev/null && [[ $(getenforce) == Enforcing ]]; then
  # A pip-installed certbot does not label its directories; nginx may only read
  # the challenge files and the private key under the expected types.
  chcon -R -u system_u -t httpd_sys_content_t "$acme_root" || echo 'Could not label the ACME webroot' >&2
fi
install -d -m 755 /etc/used
# No secrets here: only the hostname and contact address.
install -m 644 /dev/null "$state"
printf 'HTTPS_DOMAIN=%s\nHTTPS_EMAIL=%s\n' "$domain" "$email" > "$state"

certbot=$(install_certbot)

# 1. Serve the ACME challenge. The redirect and 443 listener stay absent until a
#    certificate exists, so this reload cannot break a working site.
render
echo "Requesting a certificate for $domain..."
acquire "$certbot"
if command -v getenforce >/dev/null && [[ $(getenforce) == Enforcing ]]; then
  chcon -R -t cert_t /etc/letsencrypt/live /etc/letsencrypt/archive 2>/dev/null || true
fi

# 2. Certificate present: re-render with 443 plus the HTTP redirect.
render

# 3. Cookies must not travel over plain HTTP once the site is HTTPS.
sed -i "s|^AUTH_COOKIE_SECURE=.*|AUTH_COOKIE_SECURE=true|" "$env_file"
sed -i "s|^FRONTEND_ORIGINS=.*|FRONTEND_ORIGINS=https://$domain|" "$env_file"
grep -qx 'AUTH_COOKIE_SECURE=true' "$env_file" || { echo 'Could not set AUTH_COOKIE_SECURE' >&2; exit 1; }
grep -qx "FRONTEND_ORIGINS=https://$domain" "$env_file" || { echo 'Could not set FRONTEND_ORIGINS' >&2; exit 1; }

# The wallet gateway must send the buyer back to the HTTPS origin, otherwise the
# Secure auth cookie and CSRF cookie are not sent on the return page.
set_env() {
  local key=$1 value=$2
  if grep -q "^$key=" "$env_file"; then
    sed -i "s|^$key=.*|$key=$value|" "$env_file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$env_file"
  fi
}
set_env WALLET_GATEWAY_PUBLIC_BASE_URL "https://$domain"

for service in "${services[@]}"; do
  systemctl restart "used@$service.service"
done

# 4. Renewal. certbot skips certificates that are not near expiry, so a daily
#    check is cheap and the nginx reload in the deploy hook picks up the new file.
cat > /etc/systemd/system/used-certbot-renew.service <<EOF
[Unit]
Description=Renew the Let's Encrypt certificate for Used Marketplace
After=network-online.target nginx.service

[Service]
Type=oneshot
ExecStart=$certbot renew --quiet --deploy-hook "/usr/bin/systemctl reload nginx"
EOF
cat > /etc/systemd/system/used-certbot-renew.timer <<'EOF'
[Unit]
Description=Daily Let's Encrypt renewal check

[Timer]
OnCalendar=*-*-* 03:17:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now used-certbot-renew.timer

echo 'Waiting for the nine services to report healthy...'
for attempt in $(seq 1 60); do
  if bash "$repo/deploy/ec2/check-health.sh" >/tmp/used-https-health.log 2>&1; then
    cat /tmp/used-https-health.log
    break
  fi
  [[ $attempt == 60 ]] && { cat /tmp/used-https-health.log >&2; echo 'Health checks failed after enabling HTTPS.' >&2; exit 1; }
  sleep 5
done

echo "HTTPS_READY https://$domain"
echo "Renewal: systemctl list-timers used-certbot-renew.timer"
