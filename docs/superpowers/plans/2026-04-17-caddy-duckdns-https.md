# Caddy + DuckDNS HTTPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Oracle A1 배포 프록시를 `nginx`에서 `Caddy`로 교체하고, `nooook.duckdns.org`에서 자동 HTTPS와 blue-green 전환을 검증 가능한 상태로 만든다.

**Architecture:** OCI Docker Compose 프록시 서비스를 `caddy`로 바꾸고, `deploy/Caddyfile`에 `nooook.duckdns.org` 전용 reverse proxy 구성을 둔다. `deploy/blue-green-deploy.sh`는 활성 upstream을 `reading-garden-blue`와 `reading-garden-green` 사이에서 교체한 뒤 `caddy reload`로 무중단 반영하고, smoke check는 HTTPS 공개 도메인을 기준으로 실행한다.

**Tech Stack:** Docker Compose, Caddy 2, Bash deployment scripts, GitHub Actions, DuckDNS

---

## File Map

- Create: `deploy/Caddyfile`
  - `nooook.duckdns.org` 사이트 정의
  - `request_body` 10MB 제한
  - `reading-garden-blue:8080` 기본 upstream reverse proxy
- Modify: `deploy/docker-compose.oci.yml`
  - `nginx` 서비스를 `caddy`로 교체
  - `80`, `443`, `443/udp` 포트 노출
  - `/opt/reading-garden/Caddyfile`, `/data`, `/config` 볼륨 연결
  - `container_name: reading-garden-caddy`
- Modify: `deploy/blue-green-deploy.sh`
  - `nginx.conf` 치환 제거
  - `Caddyfile` upstream 치환 + `caddy reload`
  - `SMOKE_BASE_URL=https://nooook.duckdns.org` 기본값
  - reload 실패/스모크 실패 시 롤백
- Modify: `.github/workflows/jvm-image.yml`
  - 원격 서버에 `deploy/Caddyfile` 복사
  - 더 이상 `deploy/nginx.conf`를 복사하지 않음
- Reference only: `deploy/cutover-smoke-check.sh`
  - `BASE_URL` 인자 지원은 이미 있으므로 코드 변경 없이 HTTPS smoke target으로 재사용

## Task 1: Replace OCI proxy service with Caddy

**Files:**
- Create: `deploy/Caddyfile`
- Modify: `deploy/docker-compose.oci.yml`

- [ ] **Step 1: Verify the current tree has no Caddy deployment asset**

Run:

```bash
docker run --rm -v "$PWD/deploy:/etc/caddy:ro" caddy:2 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

Expected: FAIL with an error equivalent to `open /etc/caddy/Caddyfile: no such file or directory`.

- [ ] **Step 2: Create `deploy/Caddyfile` with the minimal HTTPS reverse proxy**

```caddyfile
nooook.duckdns.org {
    encode zstd gzip

    request_body {
        max_size 10MB
    }

    reverse_proxy reading-garden-blue:8080 {
        header_up Host {host}
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }
}
```

- [ ] **Step 3: Replace the `nginx` service in `deploy/docker-compose.oci.yml` with `caddy`**

Use this service block and keep the existing `app-blue`, `app-green`, and `postgres` services unchanged:

```yaml
services:
  caddy:
    image: caddy:2
    container_name: reading-garden-caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - /opt/reading-garden/Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      app-blue:
        condition: service_healthy
```

Also extend the `volumes:` section at the bottom:

```yaml
volumes:
  pgdata:
  caddy_data:
  caddy_config:
```

- [ ] **Step 4: Validate the new compose and Caddy configuration**

Run:

```bash
docker run --rm -v "$PWD/deploy:/etc/caddy:ro" caddy:2 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose -f deploy/docker-compose.oci.yml config >/tmp/reading-garden-oci-compose.txt
rg -n "reading-garden-caddy|443:443|/opt/reading-garden/Caddyfile|caddy_data|caddy_config" /tmp/reading-garden-oci-compose.txt
```

Expected:
- `caddy validate` exits `0`
- `docker compose ... config` exits `0`
- `rg` prints the new caddy container name, HTTPS port mappings, and Caddy volumes

- [ ] **Step 5: Commit the proxy service replacement**

```bash
git add deploy/Caddyfile deploy/docker-compose.oci.yml
git commit -m "feat: replace nginx proxy with caddy"
```

## Task 2: Switch blue-green cutover from nginx restart to Caddy reload

**Files:**
- Modify: `deploy/blue-green-deploy.sh`
- Reference: `deploy/cutover-smoke-check.sh`

- [ ] **Step 1: Capture the current nginx-specific behavior**

Run:

```bash
rg -n "nginx.conf|reading-garden-proxy|docker restart|127.0.0.1:80" deploy/blue-green-deploy.sh
```

Expected: Matches are present, confirming the script still depends on nginx-era behavior and HTTP smoke checks.

- [ ] **Step 2: Introduce Caddy-specific variables and reload helpers**

Replace the nginx-specific variables at the top with this block:

```bash
APP_DIR="${REMOTE_APP_DIR:-/opt/reading-garden}"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
CADDY_FILE="${APP_DIR}/Caddyfile"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
SMOKE_BASE_URL="${SMOKE_BASE_URL:-https://nooook.duckdns.org}"

reload_caddy() {
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
    docker compose -f "$COMPOSE_FILE" exec -T caddy \
        caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
}

switch_proxy_target() {
    local from="$1"
    local to="$2"
    sed -i "s/${from}:8080/${to}:8080/" "$CADDY_FILE"
    reload_caddy
}
```

- [ ] **Step 3: Update first-deploy and cutover paths to use Caddy**

Apply these exact behavior changes:

```bash
# First deployment — no containers running yet
if ! docker ps --format '{{.Names}}' | grep -q 'reading-garden-blue\|reading-garden-green'; then
    echo "=== First deployment: starting blue + caddy + postgres ==="
    sed -i 's/reading-garden-green:8080/reading-garden-blue:8080/' "$CADDY_FILE" || true

    docker compose -f "$COMPOSE_FILE" pull
    docker compose -f "$COMPOSE_FILE" up -d
    reload_caddy

    echo "=== Waiting for app-blue to become healthy ==="
    deadline=$((SECONDS + TIMEOUT_SECONDS))
    until docker inspect --format='{{.State.Health.Status}}' reading-garden-blue 2>/dev/null | grep -q "healthy"; do
        if (( SECONDS >= deadline )); then
            echo "ERROR: app-blue did not become healthy within ${TIMEOUT_SECONDS}s" >&2
            docker compose -f "$COMPOSE_FILE" logs app-blue --tail 50
            exit 1
        fi
        sleep 2
    done

    TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"
    echo "=== First deployment complete: app-blue is active ==="
    docker system prune -f || true
    exit 0
fi
```

And replace the swap/rollback section with:

```bash
switch_proxy_target "reading-garden-${ACTIVE}" "reading-garden-${STANDBY}"
echo "=== Caddy switched to app-${STANDBY} ==="

if ! TIMEOUT_SECONDS=30 "${APP_DIR}/cutover-smoke-check.sh" "${SMOKE_BASE_URL}"; then
    echo "ERROR: Smoke check failed, rolling back to app-${ACTIVE}" >&2
    switch_proxy_target "reading-garden-${STANDBY}" "reading-garden-${ACTIVE}"
    docker compose -f "$COMPOSE_FILE" stop "app-${STANDBY}"
    exit 1
fi
```

- [ ] **Step 4: Verify the script is syntactically valid and references Caddy**

Run:

```bash
bash -n deploy/blue-green-deploy.sh
rg -n "Caddyfile|caddy reload|SMOKE_BASE_URL|https://nooook.duckdns.org" deploy/blue-green-deploy.sh
```

Expected:
- `bash -n` exits `0`
- `rg` prints the new helper block and HTTPS smoke URL

- [ ] **Step 5: Commit the deployment script cutover**

```bash
git add deploy/blue-green-deploy.sh
git commit -m "feat: switch blue-green cutover to caddy reload"
```

## Task 3: Update OCI workflow to ship Caddy assets

**Files:**
- Modify: `.github/workflows/jvm-image.yml`

- [ ] **Step 1: Confirm the workflow still deploys `nginx.conf`**

Run:

```bash
rg -n "nginx.conf|Caddyfile" .github/workflows/jvm-image.yml
```

Expected: The workflow references `deploy/nginx.conf` and does not yet reference `deploy/Caddyfile`.

- [ ] **Step 2: Replace the proxy config copy step with `deploy/Caddyfile`**

In the `Copy deployment files` step, replace the nginx copy line:

```bash
scp -P "$OCI_PORT" deploy/nginx.conf "${{ secrets.OCI_USER }}@${{ secrets.OCI_HOST }}:$REMOTE_APP_DIR/nginx.conf"
```

with:

```bash
scp -P "$OCI_PORT" deploy/Caddyfile "${{ secrets.OCI_USER }}@${{ secrets.OCI_HOST }}:$REMOTE_APP_DIR/Caddyfile"
```

Keep the rest of the deployment files unchanged.

- [ ] **Step 3: Verify the workflow now ships Caddy**

Run:

```bash
rg -n "deploy/Caddyfile|/opt/reading-garden/Caddyfile" .github/workflows/jvm-image.yml
rg -n "deploy/nginx.conf" .github/workflows/jvm-image.yml
```

Expected:
- The first `rg` prints the new Caddyfile copy line
- The second `rg` prints no matches

- [ ] **Step 4: Commit the workflow update**

```bash
git add .github/workflows/jvm-image.yml
git commit -m "chore: deploy caddy config in oci workflow"
```

## Task 4: End-to-end validation on the OCI host

**Files:**
- No code changes in this task
- Use the already modified deployment artifacts in the worktree branch

- [ ] **Step 1: Point DuckDNS and open ports before deployment**

Run these checks after updating DuckDNS and OCI rules:

```bash
dig +short nooook.duckdns.org
nc -vz 64.110.101.211 80
nc -vz 64.110.101.211 443
```

Expected:
- `dig` returns the OCI public IP
- Both `nc` checks connect successfully

- [ ] **Step 2: Deploy the branch artifacts to the OCI host**

Prerequisite: the local Docker client is already authenticated to `ghcr.io`.

Build and push a branch-specific ARM64 image, then copy the deployment artifacts and run the deploy script:

```bash
./gradlew bootJar
export TEST_IMAGE_REF="ghcr.io/readinggarden/reading-garden-kotlin:caddy-duckdns-$(git rev-parse --short HEAD)"
docker buildx build --platform linux/arm64 -t "$TEST_IMAGE_REF" --push .
scp -i /Users/ian-mac/.ssh/ian_oracle_a1 deploy/docker-compose.oci.yml ubuntu@64.110.101.211:/opt/reading-garden/docker-compose.yml
scp -i /Users/ian-mac/.ssh/ian_oracle_a1 deploy/Caddyfile ubuntu@64.110.101.211:/opt/reading-garden/Caddyfile
scp -i /Users/ian-mac/.ssh/ian_oracle_a1 deploy/blue-green-deploy.sh ubuntu@64.110.101.211:/opt/reading-garden/blue-green-deploy.sh
scp -i /Users/ian-mac/.ssh/ian_oracle_a1 deploy/cutover-smoke-check.sh ubuntu@64.110.101.211:/opt/reading-garden/cutover-smoke-check.sh
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'chmod +x /opt/reading-garden/blue-green-deploy.sh /opt/reading-garden/cutover-smoke-check.sh'
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 "cd /opt/reading-garden && IMAGE_REF=$TEST_IMAGE_REF ./blue-green-deploy.sh"
```

Expected:
- `reading-garden-caddy`, `reading-garden-blue`, and `reading-garden-db` start successfully
- The deployment script exits `0`

- [ ] **Step 3: Verify HTTPS and first deployment behavior**

Run:

```bash
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker ps --format "{{.Names}}"'
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker logs reading-garden-caddy --tail 200'
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker exec reading-garden-blue curl -f http://localhost:8080/api/health'
curl -I https://nooook.duckdns.org
curl -fsS https://nooook.duckdns.org/api/health
curl -fsS https://nooook.duckdns.org/v3/api-docs >/dev/null
```

Expected:
- Caddy logs show successful certificate provisioning or cache reuse
- Internal app health succeeds
- External HTTPS health succeeds
- `/v3/api-docs` succeeds through Caddy

- [ ] **Step 4: Verify a blue-green swap**

Trigger a second deployment with a different image tag, then run:

```bash
./gradlew bootJar
export TEST_IMAGE_REF="ghcr.io/readinggarden/reading-garden-kotlin:caddy-duckdns-swap-$(git rev-parse --short HEAD)"
docker buildx build --platform linux/arm64 -t "$TEST_IMAGE_REF" --push .
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 "cd /opt/reading-garden && IMAGE_REF=$TEST_IMAGE_REF ./blue-green-deploy.sh"
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker inspect --format="{{.State.Health.Status}}" reading-garden-blue || true'
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker inspect --format="{{.State.Health.Status}}" reading-garden-green || true'
for i in $(seq 1 10); do curl -fsS https://nooook.duckdns.org/api/health >/dev/null || break; done
ssh -i /Users/ian-mac/.ssh/ian_oracle_a1 ubuntu@64.110.101.211 'docker compose -f /opt/reading-garden/docker-compose.yml ps'
```

Expected:
- The standby color becomes healthy before traffic switches
- Repeated HTTPS health checks do not return `502`
- After cutover, the old active container is stopped

- [ ] **Step 5: Record the verification result**

Capture the exact success or failure evidence in the PR description or follow-up note. Include:

```text
- DuckDNS host: nooook.duckdns.org
- HTTPS certificate issued: yes/no
- First deploy result: success/failure
- Blue-green swap result: success/failure
- Any rollback triggered: yes/no
```

## Spec Coverage Check

- `nooook.duckdns.org` only: covered by `deploy/Caddyfile` and `SMOKE_BASE_URL`
- automatic HTTPS: covered by Caddy site-address configuration and Task 4 HTTPS validation
- blue-green 유지: covered by `deploy/blue-green-deploy.sh`
- `readinggarden.duckdns.org` untouched: no task changes that file, DNS, or workflow target
- rollback on smoke failure: covered by Task 2 rollback path and Task 4 validation

## Placeholder Scan

- No `TBD`, `TODO`, or “appropriate handling” placeholders remain
- Every code-changing step contains exact snippets
- Every verification step includes exact commands and expected results

## Type/Name Consistency Check

- Proxy service name is consistently `caddy`
- Container name is consistently `reading-garden-caddy`
- Config path is consistently `/opt/reading-garden/Caddyfile`
- Validation host is consistently `nooook.duckdns.org`
