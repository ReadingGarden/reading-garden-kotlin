#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET="${ROOT}/deploy/caddy-start.sh"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

write_timeout_stub() {
    local path="$1"
    cat > "$path" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$TMPDIR/timeout-log"
secs="${1:-}"
shift || true
if [[ -z "$secs" || "$#" -eq 0 ]]; then
    exit 1
fi

if [[ "${TIMEOUT_STUB_MODE:-}" == "force-timeout" ]]; then
    sleep "${TIMEOUT_STUB_SLEEP_SECONDS:-1}"
    exit "${TIMEOUT_STUB_EXIT_CODE:-124}"
fi

"$@"
EOF
}

assert_contains() {
    local file="$1"
    local expected="$2"
    grep -F "$expected" "$file" >/dev/null || fail "expected '$expected' in $file"
}

assert_line_equals() {
    local file="$1"
    local expected="$2"
    grep -Fx "$expected" "$file" >/dev/null || fail "expected exact line '$expected' in $file"
}

assert_file_lines_exact() {
    local file="$1"
    shift
    local expected=("$@")
    local actual=()
    local line
    while IFS= read -r line; do
        actual+=("$line")
    done < "$file"
    [[ "${#actual[@]}" -eq "${#expected[@]}" ]] || fail "expected ${#expected[@]} lines, got ${#actual[@]} in $file"
    local i
    for i in "${!expected[@]}"; do
        [[ "${actual[$i]}" == "${expected[$i]}" ]] || fail "expected line[$i] '${expected[$i]}', got '${actual[$i]}' in $file"
    done
}

test_success_execs_caddy_run() {
    (
        set -euo pipefail
        local tmp
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/bin" "$tmp/routes"

        cat > "$tmp/routes/prod-upstream.caddy" <<'EOF'
handle_path /api/* {
    rewrite * /api{path}

    reverse_proxy reading-garden-blue:8080 {
        header_up Host {host}
        header_up X-Forwarded-Proto {scheme}
    }

    encode gzip zstd
}
EOF

        cat > "$tmp/bin/getent" <<'EOF'
#!/usr/bin/env bash
printf 'getent\n' >> "$TMPDIR/event-log"
printf '%s %s\n' "${1:-}" "${2:-}" >> "$TMPDIR/getent-log"
if [[ "${1:-}" == "hosts" && "${2:-}" == "reading-garden-blue" ]]; then
    echo "172.19.0.2 reading-garden-blue"
    exit 0
fi
exit 1
EOF

        cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'curl\n' >> "$TMPDIR/event-log"
printf '%s\n' "$@" >> "$TMPDIR/curl-log"
if grep -Fx "http://reading-garden-blue:8080/api/health" "$TMPDIR/curl-log" >/dev/null; then
    exit 0
fi
exit 1
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
printf 'caddy\n' >> "$TMPDIR/event-log"
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$$" > "$TMPDIR/caddy-pid"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF
        write_timeout_stub "$tmp/bin/timeout"

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy" "$tmp/bin/timeout"

        PATH="$tmp/bin:$PATH" \
        TMPDIR="$tmp" \
        CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
        UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
        UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
        "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr" &
        local target_pid=$!
        if ! wait "$target_pid"; then
            fail "expected success path to exit 0"
        fi

        assert_file_lines_exact "$tmp/timeout-log" \
            1 \
            getent \
            hosts \
            reading-garden-blue
        assert_line_equals "$tmp/getent-log" "hosts reading-garden-blue"
        assert_file_lines_exact "$tmp/curl-log" \
            --connect-timeout \
            1 \
            --max-time \
            1 \
            -fsS \
            http://reading-garden-blue:8080/api/health
        assert_file_lines_exact "$tmp/caddy-argv" \
            run \
            --config \
            /etc/caddy/Caddyfile \
            --adapter \
            caddyfile
        assert_line_equals "$tmp/caddy-pid" "$target_pid"
        [[ -f "$tmp/event-log" ]] || fail "expected event log"
        event_lines=()
        while IFS= read -r line; do
            event_lines+=("$line")
        done < "$tmp/event-log"
        [[ "${event_lines[0]:-}" == "getent" && "${event_lines[1]:-}" == "curl" && "${event_lines[2]:-}" == "caddy" ]] || fail "expected event order getent -> curl -> caddy"
        [[ -e "$tmp/caddy-invoked" ]] || fail "expected caddy to be invoked"
    )
}

test_route_parse_failure() {
    (
        set -euo pipefail
        local tmp
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/bin" "$tmp/routes"

        cat > "$tmp/routes/prod-upstream.caddy" <<'EOF'
handle_path /api/* {
    rewrite * /api{path}

    # route block generated from the blue upstream
    not-a-reverse-proxy-line

    encode gzip zstd
}
EOF

        cat > "$tmp/bin/getent" <<'EOF'
#!/usr/bin/env bash
printf 'getent\n' >> "$TMPDIR/event-log"
printf '%s %s\n' "${1:-}" "${2:-}" >> "$TMPDIR/getent-log"
exit 0
EOF

        cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'curl\n' >> "$TMPDIR/event-log"
printf '%s\n' "$@" >> "$TMPDIR/curl-log"
exit 0
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
printf 'caddy\n' >> "$TMPDIR/event-log"
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF
        write_timeout_stub "$tmp/bin/timeout"

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy" "$tmp/bin/timeout"

        if PATH="$tmp/bin:$PATH" \
            TMPDIR="$tmp" \
            CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
            UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
            UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
            "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"; then
            fail "expected route parse failure"
        fi

        assert_contains "$tmp/stderr" "Failed to extract upstream"
        [[ ! -e "$tmp/event-log" ]] || fail "expected no events before parse failure"
        [[ ! -e "$tmp/getent-log" ]] || fail "expected getent not to be invoked"
        [[ ! -e "$tmp/curl-log" ]] || fail "expected curl not to be invoked"
        [[ ! -e "$tmp/caddy-invoked" ]] || fail "expected caddy not to be invoked"
    )
}

test_dns_failure() {
    (
        set -euo pipefail
        local tmp
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/bin" "$tmp/routes"

        cat > "$tmp/routes/prod-upstream.caddy" <<'EOF'
handle_path /api/* {
    rewrite * /api{path}

    reverse_proxy reading-garden-blue:8080 {
        header_up Host {host}
        header_up X-Forwarded-Proto {scheme}
    }

    encode gzip zstd
}
EOF

        cat > "$tmp/bin/getent" <<'EOF'
#!/usr/bin/env bash
printf '%s %s\n' "${1:-}" "${2:-}" >> "$TMPDIR/getent-log"
exit 1
EOF

        cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$TMPDIR/curl-log"
exit 0
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF
        write_timeout_stub "$tmp/bin/timeout"

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy" "$tmp/bin/timeout"

        if PATH="$tmp/bin:$PATH" \
            TMPDIR="$tmp" \
            CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
            UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
            UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
            "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"; then
            fail "expected DNS failure"
        fi

        assert_file_lines_exact "$tmp/timeout-log" \
            1 \
            getent \
            hosts \
            reading-garden-blue
        assert_line_equals "$tmp/getent-log" "hosts reading-garden-blue"
        assert_contains "$tmp/stderr" "upstream DNS"
        [[ ! -e "$tmp/curl-log" ]] || fail "expected curl not to be invoked"
        [[ ! -e "$tmp/caddy-invoked" ]] || fail "expected caddy not to be invoked"
    )
}

test_dns_probe_timeout_is_bounded() {
    (
        set -euo pipefail
        local tmp
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/bin" "$tmp/routes"

        cat > "$tmp/routes/prod-upstream.caddy" <<'EOF'
handle_path /api/* {
    rewrite * /api{path}

    reverse_proxy reading-garden-blue:8080 {
        header_up Host {host}
        header_up X-Forwarded-Proto {scheme}
    }

    encode gzip zstd
}
EOF

        cat > "$tmp/bin/getent" <<'EOF'
#!/usr/bin/env bash
printf '%s %s\n' "${1:-}" "${2:-}" >> "$TMPDIR/getent-log"
sleep 5
exit 1
EOF

        cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$TMPDIR/curl-log"
exit 0
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF
        write_timeout_stub "$tmp/bin/timeout"

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy" "$tmp/bin/timeout"

        SECONDS=0
        if PATH="$tmp/bin:$PATH" \
            TMPDIR="$tmp" \
            TIMEOUT_STUB_MODE=force-timeout \
            TIMEOUT_STUB_SLEEP_SECONDS=1 \
            TIMEOUT_STUB_EXIT_CODE=124 \
            CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
            UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
            UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
            "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"; then
            fail "expected DNS probe timeout failure"
        fi

        [[ "$SECONDS" -lt 4 ]] || fail "expected DNS probe timeout to stay within budget, got ${SECONDS}s"
        assert_file_lines_exact "$tmp/timeout-log" \
            1 \
            getent \
            hosts \
            reading-garden-blue
        assert_contains "$tmp/stderr" "Timed out waiting for upstream DNS"
        [[ ! -e "$tmp/getent-log" ]] || fail "expected timeout wrapper to intercept hanging getent"
        [[ ! -e "$tmp/curl-log" ]] || fail "expected curl not to be invoked"
        [[ ! -e "$tmp/caddy-invoked" ]] || fail "expected caddy not to be invoked"
    )
}

test_health_timeout_failure() {
    (
        set -euo pipefail
        local tmp
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/bin" "$tmp/routes"

        cat > "$tmp/routes/prod-upstream.caddy" <<'EOF'
handle_path /api/* {
    rewrite * /api{path}

    reverse_proxy reading-garden-blue:8080 {
        header_up Host {host}
        header_up X-Forwarded-Proto {scheme}
    }

    encode gzip zstd
}
EOF

        cat > "$tmp/bin/getent" <<'EOF'
#!/usr/bin/env bash
printf 'getent\n' >> "$TMPDIR/event-log"
printf '%s %s\n' "${1:-}" "${2:-}" >> "$TMPDIR/getent-log"
if [[ "${1:-}" == "hosts" && "${2:-}" == "reading-garden-blue" ]]; then
    echo "172.19.0.2 reading-garden-blue"
    exit 0
fi
exit 1
EOF

        cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'curl\n' >> "$TMPDIR/event-log"
printf '%s\n' "$@" >> "$TMPDIR/curl-log"
args=("$@")
connect_timeout=""
max_time=""
i=0
while [[ "$i" -lt "${#args[@]}" ]]; do
    case "${args[$i]}" in
        --connect-timeout)
            i=$((i + 1))
            connect_timeout="${args[$i]:-}"
            ;;
        --max-time)
            i=$((i + 1))
            max_time="${args[$i]:-}"
            ;;
    esac
    i=$((i + 1))
done
[[ -n "$connect_timeout" ]] || exit 97
[[ -n "$max_time" ]] || exit 98
sleep "$max_time"
exit 28
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
printf 'caddy\n' >> "$TMPDIR/event-log"
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF
        write_timeout_stub "$tmp/bin/timeout"

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy" "$tmp/bin/timeout"

        SECONDS=0
        if PATH="$tmp/bin:$PATH" \
            TMPDIR="$tmp" \
            CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
            UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
            UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
            "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"; then
            fail "expected health timeout failure"
        fi

        [[ "$SECONDS" -lt 4 ]] || fail "expected health probe timeout to stay within budget, got ${SECONDS}s"
        assert_file_lines_exact "$tmp/timeout-log" \
            1 \
            getent \
            hosts \
            reading-garden-blue
        assert_line_equals "$tmp/getent-log" "hosts reading-garden-blue"
        assert_file_lines_exact "$tmp/curl-log" \
            --connect-timeout \
            1 \
            --max-time \
            1 \
            -fsS \
            http://reading-garden-blue:8080/api/health
        assert_contains "$tmp/stderr" "Timed out waiting for upstream health"
        event_lines=()
        while IFS= read -r line; do
            event_lines+=("$line")
        done < "$tmp/event-log"
        [[ "${event_lines[0]:-}" == "getent" && "${event_lines[1]:-}" == "curl" ]] || fail "expected event order getent -> curl before timeout"
        [[ ! -e "$tmp/caddy-invoked" ]] || fail "expected caddy not to be invoked"
    )
}

test_success_execs_caddy_run
test_route_parse_failure
test_dns_failure
test_dns_probe_timeout_is_bounded
test_health_timeout_failure

echo "PASS: caddy startup guard"
