# Caddy + DuckDNS HTTPS 검증 설계

작성일: 2026-04-17
브랜치: `feat/caddy-https-duckdns`

## 배경

현재 Oracle A1 배포는 `nginx`가 `80` 포트만 받아 Kotlin 앱의 blue-green 컨테이너(`reading-garden-blue`, `reading-garden-green`) 중 활성 컨테이너로 프록시한다. 목표는 이 프록시 계층을 `Caddy`로 교체하고, `DuckDNS` 임시 도메인 `nooook.duckdns.org`에서 자동 HTTPS와 blue-green 전환이 함께 정상 동작하는지 검증하는 것이다.

이번 단계에서는 `readinggarden.duckdns.org`를 건드리지 않는다. 메인 서비스 컷오버는 범위 밖이다.

## 목표

- `nooook.duckdns.org`가 Oracle A1 서버를 가리키도록 구성한다.
- OCI 배포 프록시를 `nginx`에서 `Caddy`로 교체한다.
- Caddy 자동 HTTPS로 `nooook.duckdns.org` 인증서 발급 및 자동 갱신 경로를 검증한다.
- 현재 blue-green 배포 구조를 유지하면서 `Caddy reload` 기반 무중단 프록시 전환을 검증한다.
- 전환 직전/직후에도 `https://nooook.duckdns.org/api/health`가 지속적으로 성공하는지 확인한다.

## 비목표

- `readinggarden.duckdns.org` DNS 변경
- Python 레거시 서버 컷오버
- DuckDNS DNS challenge 기반 인증서 발급
- blue-green 구조 자체의 대규모 재설계

## 현재 구조 요약

- 프록시: `deploy/docker-compose.oci.yml`의 `nginx:alpine`
- 프록시 설정: `deploy/nginx.conf`
- 배포 스크립트: `deploy/blue-green-deploy.sh`
- 첫 배포 시 `app-blue` 활성화
- 이후 배포 시 대기 색상 컨테이너를 올리고 health check 통과 후 프록시 대상을 바꾼다

현재 프록시는 설정 파일 문자열 치환 후 컨테이너 restart로 전환한다. HTTPS, 인증서 저장소, 무중단 reload는 없다.

## 제안 구조

### 프록시 교체

- `nginx` 서비스를 `caddy` 서비스로 교체한다.
- 노출 포트:
  - `80:80`
  - `443:443`
  - `443:443/udp`는 HTTP/3 대응용으로 선택적으로 연다. 검증에는 필수는 아니지만 compose에는 포함한다.
- Caddy는 `/etc/caddy/Caddyfile`을 사용한다.
- Caddy 데이터는 재배포 후에도 인증서 상태가 유지되도록 named volume 또는 호스트 볼륨을 사용한다.

### Caddy 설정 방향

- 새 파일 `deploy/Caddyfile`을 추가한다.
- 대상 사이트는 `nooook.duckdns.org` 하나로 제한한다.
- Caddy는 다음 역할만 수행한다.
  - HTTP 요청 수신
  - 자동 HTTPS 리다이렉트
  - ACME 인증서 발급/갱신
  - 활성 앱 컨테이너로 reverse proxy
- reverse proxy 대상은 `reading-garden-blue:8080` 또는 `reading-garden-green:8080` 중 하나다.
- 공통 프록시 헤더는 현재 nginx와 동일 의미를 유지한다.

### 활성 업스트림 전환 방식

- `deploy/blue-green-deploy.sh`는 더 이상 `nginx.conf`를 수정하지 않는다.
- 대신 `Caddyfile` 내 `reverse_proxy` 대상을 활성 컨테이너 이름으로 치환한다.
- 치환 후 `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile`로 무중단 reload 한다.
- reload 실패 또는 smoke check 실패 시 즉시 이전 업스트림으로 되돌리고 다시 reload 한다.

이 방식을 선택한 이유는 현재 배포 구조 변경이 가장 작고, Caddy 공식 문서가 권장하는 `Caddyfile + caddy reload` 흐름과 맞기 때문이다.

## 파일별 변경 계획

### `deploy/docker-compose.oci.yml`

- `nginx` 서비스를 `caddy` 서비스로 교체
- `container_name: reading-garden-caddy`를 부여해 운영 확인과 로그 조회를 단순화
- 포트 매핑 추가:
  - `80:80`
  - `443:443`
  - `443:443/udp`
- 볼륨 추가:
  - `/opt/reading-garden/Caddyfile:/etc/caddy/Caddyfile`
  - `caddy_data:/data`
  - `caddy_config:/config`
- `depends_on`는 첫 배포 시 `app-blue` health check에 의존하게 유지
- 필요 시 Caddy 컨테이너에 `CADDY_DOMAIN=nooook.duckdns.org` 같은 환경변수를 줄 수 있지만, 이번 단계에서는 설정 단순화를 위해 도메인을 Caddyfile에 직접 적는 쪽을 우선한다

### `deploy/Caddyfile`

- 신규 추가
- 초기 active upstream은 `reading-garden-blue:8080`
- 사이트 블록은 `nooook.duckdns.org`
- reverse proxy, forwarded 헤더, body size 제한을 현재 nginx와 유사하게 맞춘다

### `deploy/blue-green-deploy.sh`

- `NGINX_CONF` 변수를 `CADDY_FILE`로 교체
- 첫 배포 시 blue upstream을 가리키도록 보장
- 후속 배포 시 active/standby 판별 로직은 유지
- 전환 시 `reverse_proxy` 대상을 standby로 바꾸고 `caddy reload`
- smoke check 실패 시 기존 upstream으로 복원 후 `caddy reload`
- 더 이상 `docker restart reading-garden-proxy`를 호출하지 않는다

### `.github/workflows/jvm-image.yml`

- 원격 서버로 복사하는 프록시 설정 파일을 `deploy/nginx.conf`에서 `deploy/Caddyfile`로 변경
- 원격 파일명도 `/opt/reading-garden/Caddyfile`로 맞춘다
- 나머지 blue-green 배포 흐름은 유지한다

### `deploy/nginx.conf`

- 새 배포 경로에서는 사용하지 않는다
- 당장 삭제할지, 레거시 참고용으로 남길지는 구현 시점에 결정한다
- 이번 설계에서는 롤백 참고용 보존을 우선한다

## 인프라 전제 조건

- DuckDNS의 `nooook.duckdns.org`를 Oracle A1 공인 IP에 연결한다
- OCI 보안 규칙과 서버 방화벽에서 아래 인바운드를 허용한다
  - `80/tcp`
  - `443/tcp`
  - `443/udp` 선택
- 서버에서 outbound HTTPS가 가능해야 ACME 인증서 발급이 된다

## 검증 계획

### 1차 검증: HTTPS 부팅

- 첫 배포 실행
- `app-blue`, `postgres`, `caddy` 기동 확인
- 서버 내부:
  - `docker exec reading-garden-blue curl -f http://localhost:8080/api/health`
  - `curl -I http://nooook.duckdns.org`
  - `curl -I https://nooook.duckdns.org`
- 외부:
  - `https://nooook.duckdns.org/api/health`
  - `https://nooook.duckdns.org/swagger-ui.html`

성공 기준:
- HTTP가 HTTPS로 리다이렉트된다
- HTTPS 핸드셰이크가 정상이고 인증서 CN/SAN이 `nooook.duckdns.org`다
- health endpoint가 200을 반환한다

### 2차 검증: 인증 포함 API

- 로그인 API 호출
- 인증이 필요한 API 1~2개 호출
- 앱 사용 흐름에서 기존 reverse proxy 헤더 처리나 스킴 처리 문제가 없는지 확인

### 3차 검증: blue-green 전환

- 첫 배포 후 두 번째 배포를 수행해 standby 색상을 올린다
- standby health check 통과 후 Caddy upstream이 새 색상으로 전환되는지 확인한다
- 전환 직전/직후 `https://nooook.duckdns.org/api/health`를 여러 번 호출해 502 또는 연결 끊김이 없는지 본다
- 최종적으로 이전 활성 컨테이너가 정지되고 새 활성 컨테이너만 서비스 중인지 확인한다

### 4차 검증: 실패 롤백

- smoke check 실패 상황을 인위적으로 만들 수 있으면 만들어 본다
- 실패 시 이전 upstream 복구 + `caddy reload` + standby 정지가 수행되는지 확인한다
- 복구 후 `https://nooook.duckdns.org/api/health`가 계속 성공해야 한다

## 운영 명령 예시

서버 내부 확인 포인트:

```bash
docker ps
docker logs reading-garden-caddy --tail 200
docker compose -f /opt/reading-garden/docker-compose.yml ps
docker exec reading-garden-blue curl -f http://localhost:8080/api/health
curl -I https://nooook.duckdns.org
```

blue-green 전환 후 확인:

```bash
docker inspect --format='{{.State.Health.Status}}' reading-garden-blue
docker inspect --format='{{.State.Health.Status}}' reading-garden-green
docker compose -f /opt/reading-garden/docker-compose.yml exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
```

## 리스크와 대응

### ACME 발급 실패

원인:
- DuckDNS가 아직 OCI IP를 가리키지 않음
- 80/443 포트가 닫혀 있음
- 서버 outbound 차단

대응:
- DNS 해석 결과와 포트 개방 상태를 먼저 검증
- Caddy 로그로 ACME challenge 실패 원인을 확인

### 프록시 전환 시 일시 오류

원인:
- 설정 치환 직후 reload 실패
- standby 앱이 healthy로 보이지만 실제 요청을 처리하지 못함

대응:
- reload 실패 시 기존 upstream 복구
- smoke check를 외부 HTTPS 경로 기준으로 유지
- 필요하면 health endpoint 외에 추가 API smoke check를 도입

### 인증서 상태 유실

원인:
- Caddy `/data` 미영속화

대응:
- compose에 persistent volume을 반드시 둔다

## 구현 순서

1. 워크트리 브랜치에서 Caddy 기반 배포 파일과 스크립트를 수정한다
2. 로컬 정적 검토와 필요한 테스트를 수행한다
3. OCI 서버에 `nooook.duckdns.org`와 포트 개방을 준비한다
4. GitHub Actions 또는 수동 배포로 첫 HTTPS 배포를 수행한다
5. HTTPS 부팅 검증 후 두 번째 배포로 blue-green 전환을 검증한다
6. 실패 복구 시나리오를 점검한다

## 구현 완료 판단 기준

- `nooook.duckdns.org`에서 유효한 HTTPS가 올라온다
- Kotlin 앱 health, Swagger, 로그인, 인증 API가 HTTPS 뒤에서 정상 동작한다
- blue-green 전환 시 Caddy reload 기반 업스트림 변경이 정상 동작한다
- 전환 실패 시 이전 업스트림으로 복구된다
- `readinggarden.duckdns.org`와 Python 서버는 이번 작업 동안 변경되지 않는다
