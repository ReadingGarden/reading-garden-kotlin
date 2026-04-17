#!/usr/bin/env bash
set -euo pipefail

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/secrets" "$TMP_DIR/data"
touch "$TMP_DIR/.env"
touch "$TMP_DIR/.runtime.env"
printf '{}\n' > "$TMP_DIR/secrets/firebase-service-account.json"

CONFIG_OUTPUT="$(
  IMAGE_REF=ghcr.io/example/reading-garden:test \
  APP_HOST_DIR="$TMP_DIR" \
  APP_CONTAINER_PREFIX=reading-garden-prod \
  APP_VOLUME_PREFIX=reading-garden-prod \
  APP_BLUE_HOST_PORT=18080 \
  APP_GREEN_HOST_PORT=18081 \
  docker compose --profile green -f deploy/docker-compose.oci.yml config
)"

printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'host_ip: 127.0.0.1'
printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'published: "18080"'
printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'published: "18081"'
printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'name: reading-garden-shared-backend'

if printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'postgres:'; then
    echo "app compose must not define postgres service" >&2
    exit 1
fi

if printf '%s\n' "$CONFIG_OUTPUT" | grep -q 'host.docker.internal=host-gateway'; then
    echo "app compose must not rely on host-gateway for postgres access" >&2
    exit 1
fi

echo "PASS"
