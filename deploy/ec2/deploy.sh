#!/usr/bin/env bash
set -euo pipefail
# Run as jenkins, from the repository root. No database or infrastructure changes.
root=/opt/used
id=${1:?Usage: deploy.sh RELEASE_ID}
[[ "$id" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Invalid release ID' >&2; exit 1; }
exec 9>"$root/deploy.lock"
flock -n 9 || { echo 'Another deployment is active' >&2; exit 1; }
services=(audit-service media-service accounts-service chat-service marketplace-service favorites-service notification-service moderation-service frontend-service)
release="$root/releases/$id"
[[ ! -e "$release" ]] || { echo 'Release already exists' >&2; exit 1; }
mkdir -m 755 "$release"
for service in "${services[@]}"; do
  jars=(services/"$service"/target/"$service"-*.jar)
  [[ ${#jars[@]} == 1 && -f "${jars[0]}" ]] || { echo "Expected one JAR for $service" >&2; exit 1; }
  install -m 644 "${jars[0]}" "$release/$service.jar"
done
(cd "$release" && sha256sum ./*.jar > SHA256SUMS)
previous=$(readlink -f "$root/current" || true)
switch_release() {
  ln -s "$1" "$root/current.next"
  mv -Tf "$root/current.next" "$root/current"
}
restart_services() {
  for service in "${services[@]}"; do
    sudo -n /usr/bin/systemctl restart "used@$service.service" || return 1
  done
}
healthy() {
  local attempt
  for attempt in $(seq 1 60); do
    if bash deploy/ec2/check-health.sh >"$root/health-last.log" 2>&1; then
      cat "$root/health-last.log"
      return 0
    fi
    sleep 5
  done
  cat "$root/health-last.log" >&2
  return 1
}
rollback() {
  trap - EXIT INT TERM
  echo 'Deployment failed; restoring previous release.' >&2
  if [[ -n "$previous" && -d "$previous" && "$previous" != "$root/current" ]]; then
    switch_release "$previous"
    if restart_services && healthy; then
      echo "Restored $previous" >&2
    else
      echo 'Rollback health failed; inspect service journals.' >&2
    fi
  else
    for service in "${services[@]}"; do
      sudo -n /usr/bin/systemctl stop "used@$service.service" || true
    done
    rm -f "$root/current"
    echo 'First deployment failed; services stopped. Database volumes retained.' >&2
  fi
  exit 1
}
switch_release "$release"
trap rollback EXIT INT TERM
restart_services
healthy
trap - EXIT INT TERM
echo "DEPLOYMENT_OK $id"
