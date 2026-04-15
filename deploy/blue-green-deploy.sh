#!/usr/bin/env bash
# Blue-Green deployment script for Reading Garden on Oracle A1
# Usage: IMAGE_REF=ghcr.io/.../...:tag ./blue-green-deploy.sh
set -euo pipefail

APP_DIR="${REMOTE_APP_DIR:-/opt/reading-garden}"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
NGINX_CONF="${APP_DIR}/nginx.conf"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

cd "$APP_DIR"

# Determine which slot is currently active
if docker ps --format '{{.Names}}' | grep -qx 'reading-garden-blue'; then
    ACTIVE="blue"
    STANDBY="green"
else
    ACTIVE="green"
    STANDBY="blue"
fi

echo "=== Current active: $ACTIVE, deploying to: $STANDBY ==="

# Pull new image
export IMAGE_REF="${IMAGE_REF:?IMAGE_REF is required}"
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
        docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
        exit 1
    fi
    sleep 2
done
echo "=== app-${STANDBY} is healthy ==="

# Switch nginx upstream to standby
sed -i "s/reading-garden-${ACTIVE}:8080/reading-garden-${STANDBY}:8080/" "$NGINX_CONF"
docker exec reading-garden-proxy nginx -s reload
echo "=== Nginx switched to app-${STANDBY} ==="

# Run smoke check against nginx
if ! TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "http://127.0.0.1:80"; then
    echo "ERROR: Smoke check failed, rolling back to app-${ACTIVE}" >&2
    sed -i "s/reading-garden-${STANDBY}:8080/reading-garden-${ACTIVE}:8080/" "$NGINX_CONF"
    docker exec reading-garden-proxy nginx -s reload
    docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
    exit 1
fi

# Stop old active container
docker compose -f "$COMPOSE_FILE" stop "app-${ACTIVE}"
echo "=== Deployment complete: app-${STANDBY} is now active ==="

# Cleanup
docker system prune -f || true
