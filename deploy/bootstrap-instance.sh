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
    cp "$SOURCE_APP_DIR/.env" "$APP_DIR/.env"
fi

if [[ ! -f "$APP_DIR/secrets/firebase-service-account.json" ]]; then
    cp "$SOURCE_APP_DIR/secrets/firebase-service-account.json" \
        "$APP_DIR/secrets/firebase-service-account.json"
fi
