#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APPLICATION_YAML="${ROOT_DIR}/src/main/resources/application.yaml"

grep -Fq "logging:" "${APPLICATION_YAML}"
grep -Fq "dateformat: \"yyyy-MM-dd'T'HH:mm:ss.SSSXXX,Asia/Seoul\"" "${APPLICATION_YAML}"

echo "PASS"
