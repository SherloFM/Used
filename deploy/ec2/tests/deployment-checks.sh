#!/usr/bin/env bash
set -euo pipefail
# Exercise release/rollback orchestration without touching systemd or the network.
# Only the deployment root and health/sleep commands are replaced in a temp copy.
source_script=$(cd "$(dirname "$0")/.." && pwd)/deploy.sh
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
services=(audit media accounts chat marketplace favorites notification moderation frontend)
for scenario in success rollback first-failure rollback-failure missing-jar; do
  fixture="$temp/$scenario"
  root="$fixture/runtime"
  mkdir -p "$root/releases" "$fixture/bin" "$fixture/deploy/ec2"
  sed "s|^root=/opt/used$|root='$root'|" "$source_script" > "$fixture/deploy/ec2/deploy.sh"
  for service in "${services[@]}"; do
    mkdir -p "$fixture/services/$service-service/target"
    printf 'test artifact\n' > "$fixture/services/$service-service/target/$service-service-1.jar"
  done
  if [[ "$scenario" != first-failure ]]; then
    mkdir "$root/releases/previous"
    ln -s "$root/releases/previous" "$root/current"
  fi
  cat > "$fixture/bin/sudo" <<'EOF'
#!/usr/bin/env bash
set -eu
[[ "$1" == -n && "$2" == /usr/bin/systemctl ]]
[[ "$3" == restart || "$3" == stop ]]
[[ "$4" =~ ^used@[a-z]+-service.service$ ]]
echo "$3 $4" >> "$TEST_ROOT/actions.log"
EOF
  printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/bin/sleep"
  chmod +x "$fixture/bin/"*
  cat > "$fixture/deploy/ec2/check-health.sh" <<'EOF'
#!/usr/bin/env bash
[[ "$TEST_SCENARIO" == success ]] && exit 0
if [[ "$TEST_SCENARIO" == rollback && $(basename "$(readlink -f "$TEST_ROOT/current")") == previous ]]; then
  exit 0
fi
echo 'Simulated unhealthy service' >&2
exit 1
EOF
  if [[ "$scenario" == missing-jar ]]; then
    rm "$fixture/services/accounts-service/target/accounts-service-1.jar"
  fi
  status=0
  (cd "$fixture" && PATH="$fixture/bin:$PATH" TEST_ROOT="$root" TEST_SCENARIO="$scenario" \
    bash deploy/ec2/deploy.sh candidate > result.log 2>&1) || status=$?
  case "$scenario" in
    success)
      [[ "$status" == 0 && $(basename "$(readlink -f "$root/current")") == candidate ]]
      [[ $(wc -l < "$root/actions.log") == 9 ]]
      ;;
    rollback|rollback-failure)
      [[ "$status" != 0 && $(basename "$(readlink -f "$root/current")") == previous ]]
      [[ $(wc -l < "$root/actions.log") == 18 ]]
      if [[ "$scenario" == rollback-failure ]]; then
        grep -q 'Rollback health failed' "$fixture/result.log"
      fi
      ;;
    first-failure)
      [[ "$status" != 0 && ! -e "$root/current" && ! -L "$root/current" ]]
      [[ $(grep -c '^stop ' "$root/actions.log") == 9 ]]
      ;;
    missing-jar)
      [[ "$status" != 0 && $(basename "$(readlink -f "$root/current")") == previous ]]
      [[ ! -e "$root/actions.log" ]]
      ;;
  esac
  echo "DEPLOYMENT_CHECK_OK $scenario"
done
