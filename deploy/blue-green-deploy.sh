#!/usr/bin/env bash
# Blue-Green deployment script for Reading Garden on Oracle A1
# Usage: IMAGE_REF=ghcr.io/.../...:tag ./blue-green-deploy.sh
set -euo pipefail

APP_DIR="${REMOTE_APP_DIR:-/opt/reading-garden}"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
EDGE_DIR="${APP_DIR}/edge"
ROUTE_DIR="${EDGE_DIR}/routes"
PROD_ROUTE_FILE="${ROUTE_DIR}/prod-upstream.caddy"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
SMOKE_BASE_URL="${SMOKE_BASE_URL:-https://readinggarden.duckdns.org}"
PUBLIC_NETWORK_NAME="${PUBLIC_NETWORK_NAME:-reading-garden-public}"

reload_caddy() {
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

route_file_has_upstream() {
    local file_path="$1"
    local target="$2"

    grep -q "reverse_proxy ${target}:8080" "$file_path"
}

route_upstream_from_file() {
    local file_path="$1"

    sed -nE 's/.*reverse_proxy ([A-Za-z0-9._-]+):8080.*/\1/p' "$file_path" | head -n 1
}

container_exists() {
    local name="$1"

    docker ps -a --format '{{.Names}}' | grep -qx "$name"
}

ensure_public_network() {
    if ! docker network inspect "$PUBLIC_NETWORK_NAME" >/dev/null 2>&1; then
        docker network create "$PUBLIC_NETWORK_NAME" >/dev/null
    fi
}

ensure_container_connected_to_public_network() {
    local container="$1"

    if ! container_exists "$container"; then
        echo "ERROR: Container not found for public network attach: $container" >&2
        return 1
    fi

    if ! docker inspect "$container" \
        --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
        | grep -qx "$PUBLIC_NETWORK_NAME"; then
        docker network connect "$PUBLIC_NETWORK_NAME" "$container"
    fi
}

caddy_container_has_startup_guard_contract() {
    if ! container_exists "reading-garden-caddy"; then
        return 1
    fi

    docker inspect "reading-garden-caddy" \
        --format '{{range .Mounts}}{{println .Destination}}{{end}}' \
        | grep -qx '/etc/caddy/routes' || return 1
    docker inspect "reading-garden-caddy" \
        --format '{{range .Mounts}}{{println .Destination}}{{end}}' \
        | grep -qx '/usr/local/bin/caddy-start.sh' || return 1
    docker inspect "reading-garden-caddy" \
        --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
        | grep -qx "$PUBLIC_NETWORK_NAME" || return 1
}

ensure_caddy_service_current() {
    local active_target="$1"
    local deadline

    ensure_container_connected_to_public_network "$active_target"

    if caddy_container_has_startup_guard_contract; then
        return 0
    fi

    docker rm -f reading-garden-caddy >/dev/null 2>&1 || true
    docker compose -f "$COMPOSE_FILE" up -d --no-deps caddy

    deadline=$((SECONDS + 60))
    until docker ps --filter name='^/reading-garden-caddy$' --filter status=running --format '{{.Names}}' | grep -qx 'reading-garden-caddy'; do
        if (( SECONDS >= deadline )); then
            echo "ERROR: Caddy did not start with the startup-guard contract" >&2
            docker logs reading-garden-caddy --tail 80 2>&1 || true
            return 1
        fi
        sleep 2
    done

    if ! caddy_container_has_startup_guard_contract; then
        echo "ERROR: Caddy container is missing startup-guard mounts or public network" >&2
        docker inspect "reading-garden-caddy" >/dev/null 2>&1 || true
        return 1
    fi
}

render_route_file_for_target() {
    local target="$1"
    local output_path="$2"

    cat > "$output_path" <<EOF
reverse_proxy ${target}:8080 {
    header_up Host {host}
    header_up X-Real-IP {remote_host}
    header_up X-Forwarded-For {remote_host}
    header_up X-Forwarded-Proto {scheme}
}
EOF
}

write_route_file_in_place() {
    local source_path="$1"

    cat "$source_path" > "$PROD_ROUTE_FILE"
}

caddy_container_has_upstream() {
    local target="$1"

    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        cat /etc/caddy/routes/prod-upstream.caddy | grep -q "reverse_proxy ${target}:8080"
}

switch_proxy_target() {
    local target="$1"
    local backup
    local candidate
    local previous_target

    mkdir -p "$ROUTE_DIR"
    backup="$(mktemp "${PROD_ROUTE_FILE}.backup.XXXXXX")"
    candidate="$(mktemp "${PROD_ROUTE_FILE}.candidate.XXXXXX")"

    if ! cp "$PROD_ROUTE_FILE" "$backup"; then
        echo "ERROR: Failed to back up prod route file" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    previous_target="$(route_upstream_from_file "$backup")"
    if [ -z "$previous_target" ]; then
        echo "ERROR: Failed to detect current prod route upstream" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    render_route_file_for_target "$target" "$candidate"
    if ! route_file_has_upstream "$candidate" "$target"; then
        echo "ERROR: Failed to render prod route for ${target}" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! write_route_file_in_place "$candidate"; then
        echo "ERROR: Failed to write prod route file" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! caddy_container_has_upstream "$target"; then
        echo "ERROR: Caddy container did not observe prod route ${target}" >&2
        write_route_file_in_place "$backup" || echo "ERROR: Failed to restore prod route after container drift" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if reload_caddy; then
        rm -f "$backup" "$candidate"
        return 0
    fi

    if ! write_route_file_in_place "$backup"; then
        echo "ERROR: Failed to restore previous prod route after reload failure" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! caddy_container_has_upstream "$previous_target"; then
        echo "ERROR: Failed to confirm restored prod route after reload failure" >&2
    else
        reload_caddy || echo "ERROR: Failed to reload Caddy after restoring prod route" >&2
    fi
    rm -f "$backup" "$candidate"
    return 1
}

cd "$APP_DIR"

export IMAGE_REF="${IMAGE_REF:?IMAGE_REF is required}"
ensure_public_network

# First deployment — no containers running yet
if ! docker ps --format '{{.Names}}' | grep -q 'reading-garden-blue\|reading-garden-green'; then
    echo "=== First deployment: starting blue + caddy + postgres ==="
    mkdir -p "$ROUTE_DIR"
    render_route_file_for_target "reading-garden-blue" "$PROD_ROUTE_FILE"

    docker compose -f "$COMPOSE_FILE" pull
    docker compose -f "$COMPOSE_FILE" up -d

    echo "=== Waiting for app-blue to become healthy ==="
    deadline=$((SECONDS + TIMEOUT_SECONDS))
    until docker inspect --format='{{.State.Health.Status}}' reading-garden-blue 2>/dev/null | grep -q "healthy"; do
        if (( SECONDS >= deadline )); then
            echo "ERROR: app-blue did not become healthy within ${TIMEOUT_SECONDS}s" >&2
            docker compose -f "$COMPOSE_FILE" logs app-blue --tail 50
            exit 1
        fi
        sleep 2
    done

    ensure_caddy_service_current "reading-garden-blue"
    TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"
    echo "=== First deployment complete: app-blue is active ==="
    docker system prune -f || true
    exit 0
fi

# Subsequent deployments — blue-green swap
current_target="$(route_upstream_from_file "$PROD_ROUTE_FILE" || true)"
case "$current_target" in
    "reading-garden-green")
        ACTIVE="green"
        STANDBY="blue"
        ;;
    *)
        ACTIVE="blue"
        STANDBY="green"
        ;;
esac

echo "=== Current active: $ACTIVE, deploying to: $STANDBY ==="

# Pull new image
docker compose -f "$COMPOSE_FILE" pull "app-${STANDBY}"

# Start standby container
if [ "$STANDBY" = "green" ]; then
    docker compose -f "$COMPOSE_FILE" --profile green up -d "app-${STANDBY}"
else
    docker compose -f "$COMPOSE_FILE" up -d "app-${STANDBY}"
fi

# Wait for standby to become healthy
echo "=== Waiting for app-${STANDBY} to become healthy ==="
deadline=$((SECONDS + TIMEOUT_SECONDS))
until docker inspect --format='{{.State.Health.Status}}' "reading-garden-${STANDBY}" 2>/dev/null | grep -q "healthy"; do
    if (( SECONDS >= deadline )); then
        echo "ERROR: app-${STANDBY} did not become healthy within ${TIMEOUT_SECONDS}s" >&2
        docker compose -f "$COMPOSE_FILE" logs "app-${STANDBY}" --tail 50
        docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
        exit 1
    fi
    sleep 2
done
echo "=== app-${STANDBY} is healthy ==="

ensure_container_connected_to_public_network "reading-garden-${STANDBY}"
ensure_caddy_service_current "reading-garden-${ACTIVE}"
switch_proxy_target "reading-garden-${STANDBY}"
echo "=== Caddy switched to app-${STANDBY} ==="

if ! TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"; then
    echo "ERROR: Smoke check failed, rolling back to app-${ACTIVE}" >&2
    switch_proxy_target "reading-garden-${ACTIVE}"
    docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
    exit 1
fi

# Stop old active container
docker compose -f "$COMPOSE_FILE" stop "app-${ACTIVE}"
echo "=== Deployment complete: app-${STANDBY} is now active ==="

# Cleanup
docker system prune -f || true
