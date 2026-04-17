#!/usr/bin/env bash
set -euo pipefail

SOURCE_APP_DIR="${SOURCE_APP_DIR:-/opt/reading-garden}"
APP_DIR="${REMOTE_APP_DIR:-${APP_DIR:-}}"

if [[ -z "$APP_DIR" ]]; then
    echo "REMOTE_APP_DIR or APP_DIR is required" >&2
    exit 1
fi

mkdir -p \
    "$APP_DIR" \
    "$APP_DIR/backups" \
    "$APP_DIR/data/images/multipart-temp" \
    "$APP_DIR/secrets"

if [[ ! -f "$APP_DIR/.env" ]]; then
    echo "Missing required env file: $APP_DIR/.env" >&2
    exit 1
fi

if [[ ! -f "$APP_DIR/secrets/firebase-service-account.json" ]]; then
    echo "Missing required secret file: $APP_DIR/secrets/firebase-service-account.json" >&2
    exit 1
fi
