#!/usr/bin/env bash
set -euo pipefail

if git ls-files --error-unmatch .env >/dev/null 2>&1; then
  echo 'Tracked .env file detected.' >&2
  exit 1
fi

if git grep -nE '^(spring\.(datasource|mail)\.password|jwt-secret)=[^$]' -- '*.properties'; then
  echo 'A literal password or JWT secret is committed in a properties file.' >&2
  exit 1
fi

echo 'TRACKED_SECRET_CHECK_OK'
