#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET="${ROOT}/deploy/caddy-start.sh"

fail() {
    echo "FAIL: $*" >&2
    exit 1
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
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy"

        PATH="$tmp/bin:$PATH" \
        TMPDIR="$tmp" \
        CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
        UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
        UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
        "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"

        assert_line_equals "$tmp/getent-log" "hosts reading-garden-blue"
        assert_line_equals "$tmp/curl-log" "http://reading-garden-blue:8080/api/health"
        assert_line_equals "$tmp/caddy-argv" "run"
        assert_line_equals "$tmp/caddy-argv" "--config"
        assert_line_equals "$tmp/caddy-argv" "/etc/caddy/Caddyfile"
        assert_line_equals "$tmp/caddy-argv" "--adapter"
        assert_line_equals "$tmp/caddy-argv" "caddyfile"
        mapfile -t event_lines < "$tmp/event-log"
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

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy"

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
exit 1
EOF

        cat > "$tmp/bin/caddy" <<'EOF'
#!/usr/bin/env bash
printf 'caddy\n' >> "$TMPDIR/event-log"
touch "$TMPDIR/caddy-invoked"
printf '%s\n' "$@" > "$TMPDIR/caddy-argv"
EOF

        chmod +x "$tmp/bin/getent" "$tmp/bin/curl" "$tmp/bin/caddy"

        if PATH="$tmp/bin:$PATH" \
            TMPDIR="$tmp" \
            CADDY_ROUTE_FILE="$tmp/routes/prod-upstream.caddy" \
            UPSTREAM_WAIT_TIMEOUT_SECONDS=1 \
            UPSTREAM_WAIT_INTERVAL_SECONDS=1 \
            "$TARGET" >"$tmp/stdout" 2>"$tmp/stderr"; then
            fail "expected health timeout failure"
        fi

        assert_line_equals "$tmp/getent-log" "hosts reading-garden-blue"
        assert_line_equals "$tmp/curl-log" "http://reading-garden-blue:8080/api/health"
        assert_contains "$tmp/stderr" "Timed out waiting for upstream health"
        mapfile -t event_lines < "$tmp/event-log"
        [[ "${event_lines[0]:-}" == "getent" && "${event_lines[1]:-}" == "curl" ]] || fail "expected event order getent -> curl before timeout"
        [[ ! -e "$tmp/caddy-invoked" ]] || fail "expected caddy not to be invoked"
    )
}

test_success_execs_caddy_run
test_route_parse_failure
test_health_timeout_failure

echo "PASS: caddy startup guard"
