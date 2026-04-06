#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://127.0.0.1:8080}}"

curl -fsS "${BASE_URL}/api/health" | python3 -c 'import json, sys
data = json.load(sys.stdin)
assert data.get("status") == "UP", data
'
curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null

echo "cutover smoke passed"
