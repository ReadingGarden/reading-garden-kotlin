#!/usr/bin/env bash
set -euo pipefail

CADDY_ROUTES_DIR="${CADDY_ROUTES_DIR:-/etc/caddy/routes}"
UPSTREAM_WAIT_TIMEOUT_SECONDS="${UPSTREAM_WAIT_TIMEOUT_SECONDS:-120}"
UPSTREAM_WAIT_INTERVAL_SECONDS="${UPSTREAM_WAIT_INTERVAL_SECONDS:-2}"
TIMEOUT_BIN=""

require_command() {
    local cmd="$1"

    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "ERROR: Required command not found: $cmd" >&2
        return 1
    fi
}

validate_positive_integer() {
    local name="$1"
    local value="$2"

    case "$value" in
        ''|*[!0-9]*)
            echo "ERROR: $name must be a positive integer: $value" >&2
            return 1
            ;;
    esac

    if [ "$value" -le 0 ]; then
        echo "ERROR: $name must be a positive integer: $value" >&2
        return 1
    fi
}

preflight_checks() {
    require_command awk
    require_command getent
    require_command curl
    require_command caddy
    require_command timeout
    validate_positive_integer UPSTREAM_WAIT_TIMEOUT_SECONDS "$UPSTREAM_WAIT_TIMEOUT_SECONDS"
    validate_positive_integer UPSTREAM_WAIT_INTERVAL_SECONDS "$UPSTREAM_WAIT_INTERVAL_SECONDS"
    TIMEOUT_BIN="$(command -v timeout)"
}

extract_upstream_hosts() {
    local routes_dir="$1"

    if [ ! -d "$routes_dir" ]; then
        echo "ERROR: Routes directory does not exist: $routes_dir" >&2
        return 1
    fi

    awk '
        $1 == "reverse_proxy" {
            for (i = 2; i <= NF; i++) {
                token = $i
                sub(/\{.*/, "", token)
                if (token ~ /:8080$/) {
                    sub(/:8080$/, "", token)
                    print token
                    break
                }
            }
        }
    ' "$routes_dir"/*.caddy 2>/dev/null | awk '!seen[$0]++'
}

remaining_budget_seconds() {
    local start_ts="$1"
    local now_ts
    local elapsed
    local remaining

    now_ts="$(date +%s)"
    elapsed=$((now_ts - start_ts))
    remaining=$((UPSTREAM_WAIT_TIMEOUT_SECONDS - elapsed))

    printf '%s\n' "$remaining"
}

calculate_probe_timeout_seconds() {
    local remaining="$1"
    local probe_timeout

    probe_timeout="$UPSTREAM_WAIT_INTERVAL_SECONDS"

    if [ "$remaining" -lt "$probe_timeout" ]; then
        probe_timeout="$remaining"
    fi

    if [ "$probe_timeout" -le 0 ]; then
        probe_timeout=1
    fi

    printf '%s\n' "$probe_timeout"
}

sleep_with_remaining_budget() {
    local start_ts="$1"
    local remaining
    local sleep_seconds

    remaining="$(remaining_budget_seconds "$start_ts")"

    if [ "$remaining" -le 0 ]; then
        return 0
    fi

    sleep_seconds="$UPSTREAM_WAIT_INTERVAL_SECONDS"
    if [ "$remaining" -lt "$sleep_seconds" ]; then
        sleep_seconds="$remaining"
    fi

    if [ "$sleep_seconds" -gt 0 ]; then
        sleep "$sleep_seconds"
    fi
}

probe_upstream_dns() {
    local host="$1"
    local probe_timeout_seconds="$2"

    "$TIMEOUT_BIN" "$probe_timeout_seconds" getent hosts "$host" >/dev/null 2>&1
}

probe_upstream_health() {
    local host="$1"
    local probe_timeout_seconds="$2"

    curl \
        --connect-timeout "$probe_timeout_seconds" \
        --max-time "$probe_timeout_seconds" \
        -fsS \
        "http://$host:8080/api/health" >/dev/null 2>&1
}

wait_for_any_upstream() {
    local hosts="$1"
    local start_ts
    local remaining
    local probe_timeout_seconds
    local host

    start_ts="$(date +%s)"
    while true; do
        remaining="$(remaining_budget_seconds "$start_ts")"
        if [ "$remaining" -le 0 ]; then
            echo "ERROR: Timed out waiting for a healthy upstream from $CADDY_ROUTES_DIR" >&2
            return 1
        fi

        for host in $hosts; do
            remaining="$(remaining_budget_seconds "$start_ts")"
            if [ "$remaining" -le 0 ]; then
                echo "ERROR: Timed out waiting for a healthy upstream from $CADDY_ROUTES_DIR" >&2
                return 1
            fi

            probe_timeout_seconds="$(calculate_probe_timeout_seconds "$remaining")"
            if probe_upstream_dns "$host" "$probe_timeout_seconds" && \
                probe_upstream_health "$host" "$probe_timeout_seconds"; then
                return 0
            fi
        done

        sleep_with_remaining_budget "$start_ts"
    done
}

main() {
    local upstream_hosts
    local upstream_hosts_inline

    preflight_checks
    upstream_hosts="$(extract_upstream_hosts "$CADDY_ROUTES_DIR")"
    if [ -z "$upstream_hosts" ]; then
        echo "ERROR: Failed to extract upstreams from $CADDY_ROUTES_DIR" >&2
        return 1
    fi

    upstream_hosts_inline="$(printf '%s\n' "$upstream_hosts" | tr '\n' ' ' | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//')"

    printf 'INFO: Waiting for any healthy upstream from %s: %s\n' \
        "$CADDY_ROUTES_DIR" "$upstream_hosts_inline" >&2
    wait_for_any_upstream "$upstream_hosts_inline"
    exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
}

main "$@"
