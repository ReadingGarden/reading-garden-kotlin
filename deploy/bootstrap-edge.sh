#!/usr/bin/env bash
set -euo pipefail

EDGE_APP_DIR="${EDGE_APP_DIR:-/opt/reading-garden/edge}"
EDGE_COMPOSE_FILE="${EDGE_APP_DIR}/docker-compose.yml"
EDGE_CADDY_FILE="${EDGE_APP_DIR}/Caddyfile"
EDGE_DEFAULTS_DIR="${EDGE_APP_DIR}/defaults"
EDGE_PROD_ROUTE_FILE="${EDGE_APP_DIR}/routes/prod-upstream.caddy"
EDGE_DEV_ROUTE_FILE="${EDGE_APP_DIR}/routes/dev-upstream.caddy"
LEGACY_APP_DIR="${LEGACY_APP_DIR:-/opt/reading-garden}"
LEGACY_CADDY_FILE="${LEGACY_APP_DIR}/Caddyfile"
PUBLIC_NETWORK_NAME="${PUBLIC_NETWORK_NAME:-reading-garden-public}"
PROD_CONTAINER_PREFIX="${PROD_CONTAINER_PREFIX:-reading-garden}"

mkdir -p "${EDGE_APP_DIR}/routes" "${EDGE_DEFAULTS_DIR}"

if [[ ! -f "$EDGE_PROD_ROUTE_FILE" && -f "${EDGE_DEFAULTS_DIR}/prod-upstream.caddy" ]]; then
    cp "${EDGE_DEFAULTS_DIR}/prod-upstream.caddy" "$EDGE_PROD_ROUTE_FILE"
fi

if [[ ! -f "$EDGE_DEV_ROUTE_FILE" && -f "${EDGE_DEFAULTS_DIR}/dev-upstream.caddy" ]]; then
    cp "${EDGE_DEFAULTS_DIR}/dev-upstream.caddy" "$EDGE_DEV_ROUTE_FILE"
fi

docker network inspect "$PUBLIC_NETWORK_NAME" >/dev/null 2>&1 || \
    docker network create "$PUBLIC_NETWORK_NAME" >/dev/null

docker volume inspect reading-garden_caddy_data >/dev/null 2>&1 || \
    docker volume create reading-garden_caddy_data >/dev/null
docker volume inspect reading-garden_caddy_config >/dev/null 2>&1 || \
    docker volume create reading-garden_caddy_config >/dev/null

for container in "${PROD_CONTAINER_PREFIX}-blue" "${PROD_CONTAINER_PREFIX}-green"; do
    if docker ps --format '{{.Names}}' | grep -qx "$container"; then
        if ! docker inspect "$container" \
            --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
            | grep -qx "$PUBLIC_NETWORK_NAME"; then
            docker network connect "$PUBLIC_NETWORK_NAME" "$container" || true
        fi
    fi
done

legacy_target="$(
    sed -nE 's/.*reverse_proxy (reading-garden-(blue|green)):8080.*/\1/p' \
        "$LEGACY_CADDY_FILE" 2>/dev/null | head -n 1
)"

if [[ -n "$legacy_target" && -f "$EDGE_PROD_ROUTE_FILE" ]]; then
    tmp_route="$(mktemp "${EDGE_PROD_ROUTE_FILE}.XXXXXX")"
    sed -E \
        "s/reverse_proxy reading-garden-(blue|green):8080/reverse_proxy ${legacy_target}:8080/" \
        "$EDGE_PROD_ROUTE_FILE" > "$tmp_route"
    cat "$tmp_route" > "$EDGE_PROD_ROUTE_FILE"
    rm -f "$tmp_route"
fi

current_caddy_mount="$(
    docker inspect reading-garden-caddy \
        --format '{{range .Mounts}}{{if eq .Destination "/etc/caddy/Caddyfile"}}{{println .Source}}{{end}}{{end}}' \
        2>/dev/null | head -n 1 || true
)"

if [[ -n "$current_caddy_mount" && "$current_caddy_mount" != "$EDGE_CADDY_FILE" ]]; then
    docker rm -f reading-garden-caddy >/dev/null
fi

docker compose -f "$EDGE_COMPOSE_FILE" up -d
