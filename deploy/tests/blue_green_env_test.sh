#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

assert_contains() {
    local file="$1"
    local pattern="$2"
    if ! grep -Fq "$pattern" "$file"; then
        echo "missing pattern '$pattern' in $file" >&2
        exit 1
    fi
}

ROUTE_RENDERER="${ROOT_DIR}/deploy/render-edge-route.sh"
BOOTSTRAP_SCRIPT="${ROOT_DIR}/deploy/bootstrap-instance.sh"
BLUE_GREEN_SCRIPT="${ROOT_DIR}/deploy/blue-green-deploy.sh"

if [[ ! -x "$ROUTE_RENDERER" ]]; then
    echo "route renderer must be executable: $ROUTE_RENDERER" >&2
    exit 1
fi

if [[ ! -x "$BOOTSTRAP_SCRIPT" ]]; then
    echo "bootstrap script must be executable: $BOOTSTRAP_SCRIPT" >&2
    exit 1
fi

assert_contains "$BLUE_GREEN_SCRIPT" 'APP_CONTAINER_PREFIX="${APP_CONTAINER_PREFIX:-reading-garden}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'EDGE_APP_DIR="${EDGE_APP_DIR:-/opt/reading-garden/edge}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'EDGE_ROUTE_FILE_NAME="${EDGE_ROUTE_FILE_NAME:-prod-upstream.caddy}"'
assert_contains "$BOOTSTRAP_SCRIPT" 'REMOTE_APP_DIR:-${APP_DIR:-}'
assert_contains "${ROOT_DIR}/deploy/bootstrap-edge.sh" 'head -n 1 || true'

"$ROUTE_RENDERER" "reading-garden" "green" > "${TMP_DIR}/prod-route.caddy"
"$ROUTE_RENDERER" "reading-garden-dev" "blue" > "${TMP_DIR}/dev-route.caddy"

assert_contains "${TMP_DIR}/prod-route.caddy" "reverse_proxy reading-garden-green:8080"
assert_contains "${TMP_DIR}/dev-route.caddy" "reverse_proxy reading-garden-dev-blue:8080"
assert_contains "${TMP_DIR}/dev-route.caddy" "header_up X-Forwarded-Proto {scheme}"

mkdir -p "${TMP_DIR}/source/secrets"
mkdir -p "${TMP_DIR}/target"
printf 'DB_HOST=postgres\n' > "${TMP_DIR}/source/.env"
printf '{}' > "${TMP_DIR}/source/secrets/firebase-service-account.json"

SOURCE_APP_DIR="${TMP_DIR}/source" REMOTE_APP_DIR="${TMP_DIR}/target" "$BOOTSTRAP_SCRIPT"

assert_contains "${TMP_DIR}/target/.env" "DB_HOST=postgres"
assert_contains "${TMP_DIR}/target/secrets/firebase-service-account.json" "{}"

echo "blue green env test passed"
