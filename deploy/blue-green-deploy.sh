#!/usr/bin/env bash
# Blue-Green deployment script for Reading Garden on Oracle A1
# Usage: IMAGE_REF=ghcr.io/.../...:tag ./blue-green-deploy.sh
set -euo pipefail

APP_DIR="${REMOTE_APP_DIR:-/opt/reading-garden}"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
CADDY_FILE="${APP_DIR}/Caddyfile"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
SMOKE_BASE_URL="${SMOKE_BASE_URL:-https://readinggarden.duckdns.org}"

reload_caddy() {
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

caddy_file_has_upstream() {
    local file_path="$1"
    local target="$2"

    grep -q "reverse_proxy ${target}:8080" "$file_path"
}

caddy_container_has_upstream() {
    local target="$1"

    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        cat /etc/caddy/Caddyfile | grep -q "reverse_proxy ${target}:8080"
}

caddy_upstream_from_file() {
    local file_path="$1"

    sed -nE \
        's/.*reverse_proxy (reading-garden-(blue|green)):8080.*/\1/p' \
        "$file_path" | head -n 1
}

render_caddyfile_for_target() {
    local target="$1"
    local output_path="$2"

    if ! grep -Eq 'reverse_proxy reading-garden-(blue|green):8080' "$CADDY_FILE"; then
        echo "ERROR: Could not find reverse_proxy upstream in ${CADDY_FILE}" >&2
        return 1
    fi

    sed -E \
        "s/reverse_proxy reading-garden-(blue|green):8080/reverse_proxy ${target}:8080/" \
        "$CADDY_FILE" > "$output_path"

    if ! caddy_file_has_upstream "$output_path" "$target"; then
        echo "ERROR: Failed to render Caddyfile for upstream ${target}" >&2
        return 1
    fi
}

write_caddyfile_in_place() {
    local source_path="$1"

    cat "$source_path" > "$CADDY_FILE"
}

switch_proxy_target() {
    local target="$1"
    local backup
    local candidate
    local previous_target

    backup="$(mktemp "${CADDY_FILE}.backup.XXXXXX")"
    candidate="$(mktemp "${CADDY_FILE}.candidate.XXXXXX")"

    if ! cp "$CADDY_FILE" "$backup"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    previous_target="$(caddy_upstream_from_file "$backup")"
    if [ -z "$previous_target" ]; then
        echo "ERROR: Failed to detect current Caddy upstream from backup" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! render_caddyfile_for_target "$target" "$candidate"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! write_caddyfile_in_place "$candidate"; then
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! caddy_container_has_upstream "$target"; then
        echo "ERROR: Caddy container did not observe upstream ${target}" >&2
        if ! write_caddyfile_in_place "$backup"; then
            echo "ERROR: Failed to restore previous Caddyfile after container drift" >&2
        fi
        rm -f "$backup" "$candidate"
        return 1
    fi

    if reload_caddy; then
        rm -f "$backup" "$candidate"
        return 0
    fi

    if ! write_caddyfile_in_place "$backup"; then
        echo "ERROR: Failed to restore previous Caddy config after reload failure" >&2
        rm -f "$backup" "$candidate"
        return 1
    fi

    if ! caddy_container_has_upstream "$previous_target"; then
        echo "ERROR: Failed to confirm restored Caddy upstream after reload failure" >&2
    elif ! reload_caddy; then
        echo "ERROR: Failed to restore previous Caddy config after reload failure" >&2
    fi
    rm -f "$backup" "$candidate"
    return 1
}

cd "$APP_DIR"

export IMAGE_REF="${IMAGE_REF:?IMAGE_REF is required}"

# First deployment — no containers running yet
if ! docker ps --format '{{.Names}}' | grep -q 'reading-garden-blue\|reading-garden-green'; then
    echo "=== First deployment: starting blue + caddy + postgres ==="
    first_target="$(mktemp "${CADDY_FILE}.first.XXXXXX")"
    if ! render_caddyfile_for_target "reading-garden-blue" "$first_target"; then
        rm -f "$first_target"
        exit 1
    fi
    if ! write_caddyfile_in_place "$first_target"; then
        rm -f "$first_target"
        exit 1
    fi
    rm -f "$first_target"

    docker compose -f "$COMPOSE_FILE" pull
    docker compose -f "$COMPOSE_FILE" up -d
    reload_caddy

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

    TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"
    echo "=== First deployment complete: app-blue is active ==="
    docker system prune -f || true
    exit 0
fi

# Subsequent deployments — blue-green swap
if docker ps --format '{{.Names}}' | grep -qx 'reading-garden-blue'; then
    ACTIVE="blue"
    STANDBY="green"
else
    ACTIVE="green"
    STANDBY="blue"
fi

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
