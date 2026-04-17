#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
APP_HOST_DIR="${TMP_DIR}/reading-garden-dev"

mkdir -p "${APP_HOST_DIR}/secrets" "${APP_HOST_DIR}/data"
printf 'DB_PASSWORD=test-password\n' > "${APP_HOST_DIR}/.env"
printf '{}' > "${APP_HOST_DIR}/secrets/firebase-service-account.json"

assert_file() {
    local path="$1"
    if [[ ! -f "$path" ]]; then
        echo "missing file: $path" >&2
        exit 1
    fi
}

assert_contains() {
    local file="$1"
    local pattern="$2"
    if ! grep -Fq -- "$pattern" "$file"; then
        echo "missing pattern '$pattern' in $file" >&2
        exit 1
    fi
}

EDGE_COMPOSE="${ROOT_DIR}/deploy/docker-compose.edge.yml"
EDGE_CADDYFILE="${ROOT_DIR}/deploy/Caddyfile.edge"
PROD_ROUTE="${ROOT_DIR}/deploy/routes/prod-upstream.caddy"
DEV_ROUTE="${ROOT_DIR}/deploy/routes/dev-upstream.caddy"
EDGE_START_SCRIPT="${ROOT_DIR}/deploy/caddy-start.sh"
APP_COMPOSE="${ROOT_DIR}/deploy/docker-compose.oci.yml"
PROD_WORKFLOW="${ROOT_DIR}/.github/workflows/jvm-image.yml"
DEV_WORKFLOW="${ROOT_DIR}/.github/workflows/jvm-image-dev.yml"

assert_file "$EDGE_COMPOSE"
assert_file "$EDGE_CADDYFILE"
assert_file "$PROD_ROUTE"
assert_file "$DEV_ROUTE"
assert_file "$EDGE_START_SCRIPT"
assert_file "$APP_COMPOSE"
assert_file "$PROD_WORKFLOW"
assert_file "$DEV_WORKFLOW"

docker compose -f "$EDGE_COMPOSE" config > "${TMP_DIR}/edge-compose.yaml"

assert_contains "$EDGE_CADDYFILE" "readinggarden.duckdns.org"
assert_contains "$EDGE_CADDYFILE" "readinggarden-dev.duckdns.org"
assert_contains "$EDGE_CADDYFILE" "/etc/caddy/routes/prod-upstream.caddy"
assert_contains "$EDGE_CADDYFILE" "/etc/caddy/routes/dev-upstream.caddy"
assert_contains "$EDGE_CADDYFILE" "log {"
assert_contains "$EDGE_CADDYFILE" "output stdout"
assert_contains "$EDGE_CADDYFILE" "format json"
assert_contains "$PROD_ROUTE" "reverse_proxy reading-garden-blue:8080"
assert_contains "$DEV_ROUTE" "reverse_proxy reading-garden-dev-blue:8080"
assert_contains "${TMP_DIR}/edge-compose.yaml" "reading-garden-public"
assert_contains "${TMP_DIR}/edge-compose.yaml" "entrypoint:"
assert_contains "${TMP_DIR}/edge-compose.yaml" "/usr/local/bin/caddy-start.sh"
assert_contains "${TMP_DIR}/edge-compose.yaml" "/opt/reading-garden/edge/caddy-start.sh"
assert_contains "${TMP_DIR}/edge-compose.yaml" 'UPSTREAM_WAIT_TIMEOUT_SECONDS: "120"'
assert_contains "${TMP_DIR}/edge-compose.yaml" 'UPSTREAM_WAIT_INTERVAL_SECONDS: "2"'

IMAGE_REF="ghcr.io/example/reading-garden:test" \
APP_HOST_DIR="${APP_HOST_DIR}" \
APP_CONTAINER_PREFIX="reading-garden-dev" \
APP_VOLUME_PREFIX="reading-garden-dev" \
APP_STOP_GRACE_PERIOD="35s" \
APP_BLUE_HOST_PORT="18090" \
APP_GREEN_HOST_PORT="18091" \
docker compose --profile green -f "$APP_COMPOSE" config > "${TMP_DIR}/app-compose.yaml"

if grep -Fq "reading-garden-caddy" "${TMP_DIR}/app-compose.yaml"; then
    echo "app compose should not include caddy service" >&2
    exit 1
fi

assert_contains "${TMP_DIR}/app-compose.yaml" "container_name: reading-garden-dev-blue"
assert_contains "${TMP_DIR}/app-compose.yaml" "container_name: reading-garden-dev-green"
assert_contains "${TMP_DIR}/app-compose.yaml" "stop_grace_period: 35s"
assert_contains "${TMP_DIR}/app-compose.yaml" "published: \"18090\""
assert_contains "${TMP_DIR}/app-compose.yaml" "published: \"18091\""
assert_contains "${TMP_DIR}/app-compose.yaml" "host.docker.internal=host-gateway"
assert_contains "${TMP_DIR}/app-compose.yaml" "source: ${APP_HOST_DIR}/data"
assert_contains "${TMP_DIR}/app-compose.yaml" "source: ${APP_HOST_DIR}/secrets/firebase-service-account.json"
if grep -Fq "container_name: reading-garden-dev-db" "${TMP_DIR}/app-compose.yaml"; then
    echo "app compose should not include postgres service" >&2
    exit 1
fi
if grep -Fq "name: reading-garden-public" "${TMP_DIR}/app-compose.yaml"; then
    echo "app compose should not depend on public docker network" >&2
    exit 1
fi

assert_contains "$PROD_WORKFLOW" "branches:"
assert_contains "$PROD_WORKFLOW" "- main"
assert_contains "$PROD_WORKFLOW" "EDGE_APP_DIR: /opt/reading-garden/edge"
assert_contains "$PROD_WORKFLOW" 'EDGE_ROUTE_FILE_NAME="prod-upstream.caddy"'
assert_contains "$PROD_WORKFLOW" "deploy/caddy-start.sh"
assert_contains "$DEV_WORKFLOW" "- dev"
assert_contains "$DEV_WORKFLOW" 'IMAGE_REF: ghcr.io/readinggarden/reading-garden-kotlin:jvm-dev-${{ github.sha }}'
assert_contains "$DEV_WORKFLOW" 'APP_CONTAINER_PREFIX="reading-garden-dev"'
assert_contains "$DEV_WORKFLOW" 'EDGE_ROUTE_FILE_NAME="dev-upstream.caddy"'
assert_contains "$DEV_WORKFLOW" "deploy/caddy-start.sh"

echo "deploy layout test passed"
