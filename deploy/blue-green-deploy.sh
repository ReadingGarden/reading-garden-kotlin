#!/usr/bin/env bash
# Blue-Green deployment script for Reading Garden on Oracle A1
# Usage: IMAGE_REF=ghcr.io/.../...:tag ./blue-green-deploy.sh
set -euo pipefail

APP_DIR="${REMOTE_APP_DIR:-/opt/reading-garden}"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
SMOKE_BASE_URL="${SMOKE_BASE_URL:-https://readinggarden.duckdns.org}"
APP_CONTAINER_PREFIX="${APP_CONTAINER_PREFIX:-reading-garden}"
APP_VOLUME_PREFIX="${APP_VOLUME_PREFIX:-$APP_CONTAINER_PREFIX}"
APP_HOST_DIR="${APP_HOST_DIR:-$APP_DIR}"
EDGE_APP_DIR="${EDGE_APP_DIR:-/opt/reading-garden/edge}"
EDGE_COMPOSE_FILE="${EDGE_APP_DIR}/docker-compose.yml"
EDGE_ROUTE_FILE_NAME="${EDGE_ROUTE_FILE_NAME:-prod-upstream.caddy}"
EDGE_ROUTE_FILE="${EDGE_APP_DIR}/routes/${EDGE_ROUTE_FILE_NAME}"
EDGE_ROUTE_FILE_IN_CONTAINER="/etc/caddy/routes/${EDGE_ROUTE_FILE_NAME}"
PUBLIC_NETWORK_NAME="${PUBLIC_NETWORK_NAME:-reading-garden-public}"
ROUTE_RENDERER="${APP_DIR}/render-edge-route.sh"

reload_caddy() {
    docker compose -f "$EDGE_COMPOSE_FILE" exec -T caddy \
        caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
    docker compose -f "$EDGE_COMPOSE_FILE" exec -T caddy \
        caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

route_file_has_upstream() {
    local file_path="$1"
    local target="$2"

    grep -q "reverse_proxy ${target}:8080" "$file_path"
}

edge_route_has_upstream() {
    local target="$1"

    docker compose -f "$EDGE_COMPOSE_FILE" exec -T caddy \
        sh -lc "grep -q 'reverse_proxy ${target}:8080' '${EDGE_ROUTE_FILE_IN_CONTAINER}'"
}

route_file_current_upstream() {
    local file_path="$1"

    sed -nE \
        "s/.*reverse_proxy (${APP_CONTAINER_PREFIX}-(blue|green)):8080.*/\\1/p" \
        "$file_path" | head -n 1
}

render_route_file_for_target() {
    local target="$1"
    local output_path="$2"
    local color="${target##*-}"

    "$ROUTE_RENDERER" "$APP_CONTAINER_PREFIX" "$color" > "$output_path"

    if ! route_file_has_upstream "$output_path" "$target"; then
        echo "ERROR: Failed to render edge route for ${target}" >&2
        return 1
    fi
}

write_route_file_in_place() {
    local source_path="$1"

    cat "$source_path" > "$EDGE_ROUTE_FILE"
}

switch_proxy_target() {
    local target="$1"
    local backup
    local candidate
    local previous_target

    backup="$(mktemp "${EDGE_ROUTE_FILE}.backup.XXXXXX")"
    candidate="$(mktemp "${EDGE_ROUTE_FILE}.candidate.XXXXXX")"

    if ! cp "$EDGE_ROUTE_FILE" "$backup"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    previous_target="$(route_file_current_upstream "$backup")"
    if [[ -z "$previous_target" ]]; then
        echo "ERROR: Failed to detect current upstream from ${EDGE_ROUTE_FILE}" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! render_route_file_for_target "$target" "$candidate"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! write_route_file_in_place "$candidate"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! edge_route_has_upstream "$target"; then
        echo "ERROR: Edge Caddy did not observe upstream ${target}" >&2
        write_route_file_in_place "$backup" || true
        rm -f "$backup" "$candidate"
        return 1
    fi

    if reload_caddy; then
        rm -f "$backup" "$candidate"
        return 0
    fi

    write_route_file_in_place "$backup" || true
    if edge_route_has_upstream "$previous_target"; then
        reload_caddy || true
    fi
    rm -f "$backup" "$candidate"
    return 1
}

container_exists() {
    local name="$1"

    docker ps --format '{{.Names}}' | grep -qx "$name"
}

ensure_container_connected_to_public_network() {
    local container="$1"

    if ! docker inspect "$container" \
        --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
        | grep -qx "$PUBLIC_NETWORK_NAME"; then
        docker network connect "$PUBLIC_NETWORK_NAME" "$container"
    fi
}

cd "$APP_DIR"

export IMAGE_REF="${IMAGE_REF:?IMAGE_REF is required}"
export APP_HOST_DIR
export APP_CONTAINER_PREFIX
export APP_VOLUME_PREFIX

if ! container_exists "${APP_CONTAINER_PREFIX}-blue" && ! container_exists "${APP_CONTAINER_PREFIX}-green"; then
    echo "=== First deployment: starting blue + postgres ==="

    docker compose -f "$COMPOSE_FILE" pull
    docker compose -f "$COMPOSE_FILE" up -d app-blue postgres

    echo "=== Waiting for app-blue to become healthy ==="
    deadline=$((SECONDS + TIMEOUT_SECONDS))
    until docker inspect --format='{{.State.Health.Status}}' "${APP_CONTAINER_PREFIX}-blue" 2>/dev/null | grep -q "healthy"; do
        if (( SECONDS >= deadline )); then
            echo "ERROR: app-blue did not become healthy within ${TIMEOUT_SECONDS}s" >&2
            docker compose -f "$COMPOSE_FILE" logs app-blue --tail 50
            exit 1
        fi
        sleep 2
    done

    ensure_container_connected_to_public_network "${APP_CONTAINER_PREFIX}-blue"
    switch_proxy_target "${APP_CONTAINER_PREFIX}-blue"

    TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"
    echo "=== First deployment complete: app-blue is active ==="
    docker system prune -f || true
    exit 0
fi

current_target="$(route_file_current_upstream "$EDGE_ROUTE_FILE" || true)"
case "$current_target" in
    "${APP_CONTAINER_PREFIX}-green")
        ACTIVE="green"
        STANDBY="blue"
        ;;
    *)
        ACTIVE="blue"
        STANDBY="green"
        ;;
esac

echo "=== Current active: $ACTIVE, deploying to: $STANDBY ==="

docker compose -f "$COMPOSE_FILE" pull "app-${STANDBY}"

if [[ "$STANDBY" = "green" ]]; then
    docker compose -f "$COMPOSE_FILE" --profile green up -d "app-${STANDBY}"
else
    docker compose -f "$COMPOSE_FILE" up -d "app-${STANDBY}"
fi

echo "=== Waiting for app-${STANDBY} to become healthy ==="
deadline=$((SECONDS + TIMEOUT_SECONDS))
until docker inspect --format='{{.State.Health.Status}}' "${APP_CONTAINER_PREFIX}-${STANDBY}" 2>/dev/null | grep -q "healthy"; do
    if (( SECONDS >= deadline )); then
        echo "ERROR: app-${STANDBY} did not become healthy within ${TIMEOUT_SECONDS}s" >&2
        docker compose -f "$COMPOSE_FILE" logs "app-${STANDBY}" --tail 50
        docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
        exit 1
    fi
    sleep 2
done
echo "=== app-${STANDBY} is healthy ==="

ensure_container_connected_to_public_network "${APP_CONTAINER_PREFIX}-${STANDBY}"
switch_proxy_target "${APP_CONTAINER_PREFIX}-${STANDBY}"
echo "=== Edge Caddy switched to app-${STANDBY} ==="

if ! TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"; then
    echo "ERROR: Smoke check failed, rolling back to app-${ACTIVE}" >&2
    switch_proxy_target "${APP_CONTAINER_PREFIX}-${ACTIVE}"
    docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
    exit 1
fi

docker compose -f "$COMPOSE_FILE" stop "app-${ACTIVE}"
echo "=== Deployment complete: app-${STANDBY} is now active ==="

docker system prune -f || true
