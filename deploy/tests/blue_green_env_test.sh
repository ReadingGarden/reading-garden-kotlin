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

assert_file_equals() {
    local file="$1"
    local expected="$2"
    local actual
    actual="$(cat "$file")"
    if [[ "$actual" != "$expected" ]]; then
        echo "unexpected file contents in $file" >&2
        echo "--- expected ---" >&2
        printf '%s\n' "$expected" >&2
        echo "--- actual ---" >&2
        printf '%s\n' "$actual" >&2
        exit 1
    fi
}

ROUTE_RENDERER="${ROOT_DIR}/deploy/render-edge-route.sh"
BOOTSTRAP_SCRIPT="${ROOT_DIR}/deploy/bootstrap-instance.sh"
BOOTSTRAP_EDGE_SCRIPT="${ROOT_DIR}/deploy/bootstrap-edge.sh"
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
assert_contains "$BLUE_GREEN_SCRIPT" 'HOST_CADDY_UPSTREAM_FILE="${HOST_CADDY_UPSTREAM_FILE:?HOST_CADDY_UPSTREAM_FILE is required}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'APP_BLUE_HOST_PORT="${APP_BLUE_HOST_PORT:?APP_BLUE_HOST_PORT is required}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'APP_GREEN_HOST_PORT="${APP_GREEN_HOST_PORT:?APP_GREEN_HOST_PORT is required}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'CUTOVER_DRAIN_SECONDS="${CUTOVER_DRAIN_SECONDS:-30}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'APP_STOP_TIMEOUT_SECONDS="${APP_STOP_TIMEOUT_SECONDS:-35}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'sleep "$CUTOVER_DRAIN_SECONDS"'
assert_contains "$BLUE_GREEN_SCRIPT" 'docker compose -f "$COMPOSE_FILE" stop -t "$APP_STOP_TIMEOUT_SECONDS" "app-${ACTIVE}"'
assert_contains "$BLUE_GREEN_SCRIPT" 'ROUTE_RENDERER="${ROUTE_RENDERER:-${APP_DIR}/render-host-caddy-upstream.sh}"'
assert_contains "$BOOTSTRAP_SCRIPT" 'REMOTE_APP_DIR:-${APP_DIR:-}'
assert_contains "$BOOTSTRAP_SCRIPT" 'SHARED_POSTGRES_HOST="${SHARED_POSTGRES_HOST:-shared-postgres}"'
assert_contains "$BOOTSTRAP_SCRIPT" 'SHARED_POSTGRES_PORT="${SHARED_POSTGRES_PORT:-5432}"'
assert_contains "$BOOTSTRAP_EDGE_SCRIPT" 'EDGE_CADDY_START_SCRIPT="${EDGE_APP_DIR}/caddy-start.sh"'

"$ROUTE_RENDERER" "reading-garden" "green" > "${TMP_DIR}/prod-route.caddy"
"$ROUTE_RENDERER" "reading-garden-dev" "blue" > "${TMP_DIR}/dev-route.caddy"

assert_contains "${TMP_DIR}/prod-route.caddy" "reverse_proxy reading-garden-green:8080"
assert_contains "${TMP_DIR}/dev-route.caddy" "reverse_proxy reading-garden-dev-blue:8080"
assert_contains "${TMP_DIR}/dev-route.caddy" "header_up X-Forwarded-Proto {scheme}"

mkdir -p "${TMP_DIR}/source/secrets"
mkdir -p "${TMP_DIR}/target/secrets"
printf 'DB_HOST=postgres\n' > "${TMP_DIR}/source/.env"
printf '{}' > "${TMP_DIR}/source/secrets/firebase-service-account.json"
printf 'DB_HOST=target-postgres\n' > "${TMP_DIR}/target/.env"
printf '{"target":true}\n' > "${TMP_DIR}/target/secrets/firebase-service-account.json"

printf 'DB_NAME=reading_garden_dev\n' >> "${TMP_DIR}/target/.env"

SOURCE_APP_DIR="${TMP_DIR}/source" REMOTE_APP_DIR="${TMP_DIR}/target" "$BOOTSTRAP_SCRIPT"

assert_contains "${TMP_DIR}/target/.env" "DB_HOST=target-postgres"
assert_contains "${TMP_DIR}/target/secrets/firebase-service-account.json" '"target":true'
assert_contains "${TMP_DIR}/target/.runtime.env" "DB_HOST=shared-postgres"
assert_contains "${TMP_DIR}/target/.runtime.env" "SPRING_DATASOURCE_URL=jdbc:postgresql://shared-postgres:5432/reading_garden_dev"

mkdir -p "${TMP_DIR}/edge/defaults" "${TMP_DIR}/edge/routes" "${TMP_DIR}/legacy" "${TMP_DIR}/bin"
cat > "${TMP_DIR}/edge/defaults/prod-upstream.caddy" <<'EOF'
reverse_proxy reading-garden-blue:8080 {
    header_up Host {host}
}
EOF
cat > "${TMP_DIR}/edge/defaults/dev-upstream.caddy" <<'EOF'
reverse_proxy reading-garden-dev-blue:8080 {
    header_up Host {host}
}
EOF
cat > "${TMP_DIR}/edge/routes/prod-upstream.caddy" <<'EOF'
reverse_proxy reading-garden-green:8080 {
    header_up Host {host}
}
EOF
cat > "${TMP_DIR}/legacy/Caddyfile" <<'EOF'
reverse_proxy reading-garden-blue:8080 {
    header_up Host {host}
}
EOF
cat > "${TMP_DIR}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "inspect" ]]; then
    exit 1
fi

if [[ "${1:-}" == "ps" ]]; then
    exit 0
fi

if [[ "${1:-}" == "network" || "${1:-}" == "volume" || "${1:-}" == "compose" ]]; then
    exit 0
fi

echo "unexpected docker invocation: $*" >&2
exit 1
EOF
chmod +x "${TMP_DIR}/bin/docker"

PATH="${TMP_DIR}/bin:$PATH" \
EDGE_APP_DIR="${TMP_DIR}/edge" \
LEGACY_APP_DIR="${TMP_DIR}/legacy" \
"$BOOTSTRAP_EDGE_SCRIPT"

assert_file_equals "${TMP_DIR}/edge/routes/prod-upstream.caddy" "$(cat <<'EOF'
reverse_proxy reading-garden-green:8080 {
    header_up Host {host}
}
EOF
)"

echo "blue green env test passed"
