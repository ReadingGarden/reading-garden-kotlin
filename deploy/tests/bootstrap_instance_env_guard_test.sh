#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BOOTSTRAP_SCRIPT="${ROOT_DIR}/deploy/bootstrap-instance.sh"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

SOURCE_DIR="${TMP_ROOT}/source"
TARGET_DIR="${TMP_ROOT}/target"

mkdir -p "${SOURCE_DIR}/secrets" "${TARGET_DIR}"
printf 'DB_NAME=prod\n' > "${SOURCE_DIR}/.env"
printf '{}\n' > "${SOURCE_DIR}/secrets/firebase-service-account.json"

if REMOTE_APP_DIR="${TARGET_DIR}" SOURCE_APP_DIR="${SOURCE_DIR}" "${BOOTSTRAP_SCRIPT}" 2>/dev/null; then
    echo "bootstrap should fail when target env files are absent" >&2
    exit 1
fi

echo "PASS"
