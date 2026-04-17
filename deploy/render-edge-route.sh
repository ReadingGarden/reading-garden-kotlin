#!/usr/bin/env bash
set -euo pipefail

APP_CONTAINER_PREFIX="${1:?usage: render-edge-route.sh <container-prefix> <blue|green>}"
TARGET_COLOR="${2:?usage: render-edge-route.sh <container-prefix> <blue|green>}"

case "$TARGET_COLOR" in
    blue|green)
        ;;
    *)
        echo "target color must be blue or green: $TARGET_COLOR" >&2
        exit 1
        ;;
esac

cat <<EOF
reverse_proxy ${APP_CONTAINER_PREFIX}-${TARGET_COLOR}:8080 {
    header_up Host {host}
    header_up X-Real-IP {remote_host}
    header_up X-Forwarded-For {remote_host}
    header_up X-Forwarded-Proto {scheme}
}
EOF
