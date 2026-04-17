#!/usr/bin/env bash
set -euo pipefail

CADDY_ROUTE_FILE="${CADDY_ROUTE_FILE:-/etc/caddy/routes/prod-upstream.caddy}"
UPSTREAM_WAIT_TIMEOUT_SECONDS="${UPSTREAM_WAIT_TIMEOUT_SECONDS:-120}"
UPSTREAM_WAIT_INTERVAL_SECONDS="${UPSTREAM_WAIT_INTERVAL_SECONDS:-2}"

extract_upstream_host() {
    local route_file="$1"
    local upstream

    upstream="$(sed -n 's/^[[:space:]]*reverse_proxy[[:space:]]\([^[:space:]]*\):8080[[:space:]]*{.*/\1/p' "$route_file" | head -n 1)"
    if [ -z "$upstream" ]; then
        echo "ERROR: Failed to extract upstream from $route_file" >&2
        return 1
    fi

    printf '%s\n' "$upstream"
}

wait_for_upstream_dns() {
    local host="$1"
    local start_ts
    local now_ts

    start_ts="$(date +%s)"
    while true; do
        if getent hosts "$host" >/dev/null 2>&1; then
            return 0
        fi

        now_ts="$(date +%s)"
        if [ $((now_ts - start_ts)) -ge "$UPSTREAM_WAIT_TIMEOUT_SECONDS" ]; then
            echo "ERROR: Timed out waiting for upstream DNS: $host" >&2
            return 1
        fi

        sleep "$UPSTREAM_WAIT_INTERVAL_SECONDS"
    done
}

wait_for_upstream_health() {
    local host="$1"
    local start_ts
    local now_ts

    start_ts="$(date +%s)"
    while true; do
        if curl -fsS "http://$host:8080/api/health" >/dev/null 2>&1; then
            return 0
        fi

        now_ts="$(date +%s)"
        if [ $((now_ts - start_ts)) -ge "$UPSTREAM_WAIT_TIMEOUT_SECONDS" ]; then
            echo "ERROR: Timed out waiting for upstream health: $host" >&2
            return 1
        fi

        sleep "$UPSTREAM_WAIT_INTERVAL_SECONDS"
    done
}

main() {
    local upstream_host

    upstream_host="$(extract_upstream_host "$CADDY_ROUTE_FILE")"
    echo "INFO: Waiting for upstream $upstream_host from $CADDY_ROUTE_FILE" >&2
    wait_for_upstream_dns "$upstream_host"
    wait_for_upstream_health "$upstream_host"
    exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
}

main "$@"
