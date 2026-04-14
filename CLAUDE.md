# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

레거시 Python 백엔드(`back/`)를 Kotlin/Spring Boot 4.0.5로 마이그레이션하는 프로젝트. Flutter 모바일 앱(`external/ReadingGarden-App`)과의 API 호환성을 유지해야 한다.

## Build & Run Commands

Gradle wrapper만 사용한다.

```bash
./gradlew bootRun                    # 로컬 JVM 실행
./gradlew test                       # 전체 테스트 실행
./gradlew build                      # 컴파일 + 테스트 + 패키징
./gradlew bootBuildImage             # 로컬 JVM Docker 이미지 빌드
./gradlew bootBuildNativeAmd64Image  # 프로덕션 linux/amd64 네이티브 이미지 빌드
```

단일 테스트 실행:
```bash
./gradlew test --tests "std.nooook.readinggardenkotlin.modules.auth.AuthControllerMvcTest"
./gradlew test --tests "*.AuthControllerMvcTest.특정메서드이름"
```

로컬 실행 시 환경변수 로딩이 필요하면 `scripts/bootrun-local.sh` 사용. `.env`와 `env/local.firebase.env` 파일이 필요하다.

## Architecture

```
src/main/kotlin/std/nooook/readinggardenkotlin/
├── common/           # 공유 인프라 (security, config, storage, api, exception)
└── modules/          # 도메인 모듈
    ├── auth/         # 인증 (회원가입, 로그인, JWT, 비밀번호 재설정)
    ├── book/         # 도서 (CRUD, 알라딘 API 연동, 독서 기록)
    ├── garden/       # 정원 (그룹 독서 공간, 멤버십)
    ├── memo/         # 메모 (독서 노트, 이미지 첨부)
    ├── push/         # 푸시 알림 (Firebase FCM)
    └── scheduler/    # 스케줄러 (작업 실행, 레거시 APScheduler 호환)
```

각 모듈은 `controller/`, `service/`, `repository/`, `entity/`, `integration/` 하위 패키지로 구성된다. 서비스 레이어는 읽기(`*QueryService`)와 쓰기(`*CommandService`)를 분리한다.

### 레거시 API 호환

- 모든 API 응답은 `LegacyHttpResponse` 또는 `LegacyDataResponse<T>` envelope으로 감싼다 (`resp_code`, `resp_msg`, `data`)
- 요청/응답 필드명은 snake_case (`user_email`, `user_password` 등)
- 변경 시 반드시 Flutter 앱의 실제 API 사용 패턴(라우트, HTTP 메서드, 쿼리 파라미터, 인증 헤더, 응답 필드명, 이미지 경로)과 호환성 확인

### 인증 흐름

JWT 기반. `/api/v1/auth/login` → Bearer 토큰 발급 → `LegacyJwtAuthenticationFilter`에서 검증 → `LegacyAuthenticationPrincipal` (userNo, userNick) 설정.

### GraalVM 네이티브 이미지

JJWT, Hibernate 등에 대한 GraalVM reflection 힌트가 `common/config/JjwtNativeHints.kt`, `HibernateNativeHints.kt`에 등록되어 있다. 새로운 라이브러리 추가 시 네이티브 이미지 힌트가 필요할 수 있다.

## Database

- MySQL, Hibernate DDL 모드: `validate` (스키마 자동 생성 없음, 사전에 존재해야 함)
- `PhysicalNamingStrategyStandardImpl` 사용 — 엔티티의 `@Table(name = "BOOK")` 등 대문자 테이블명이 그대로 MySQL에 매핑됨
- 레거시 DDL과 정확히 일치해야 함 (대문자 테이블명: `USER`, `BOOK`, `MEMO`, `GARDEN` 등)

## Testing

- 테스트 DB: H2 인메모리 (MySQL 모드, `src/test/resources/application.yaml`)
- 테스트 DDL: `create-drop` (테스트 시 자동 스키마 생성/삭제)
- `*MvcTest`: MockMvc 기반 슬라이스 테스트 (컨트롤러 레이어)
- `*IntegrationTest`: `@SpringBootTest` + MockMvc 풀 컨텍스트 통합 테스트
- `contracts/legacy/`: 레거시 Python API 응답 JSON fixture로 호환성 검증
- 통합 테스트에서 `MailSender`, `TaskScheduler` 등은 `@TestConfiguration`으로 recording stub 주입

## CI/CD & Deployment

- `main` 브랜치 push → GitHub Actions (`.github/workflows/native-amd64-image.yml`)
- 테스트 → 네이티브 이미지 빌드 → GHCR push → EC2 SSH 배포 → smoke check → 실패 시 자동 롤백
- 프로덕션: AWS EC2 t2.micro, Ubuntu 24.04 amd64
- 런타임 매니페스트: `deploy/docker-compose.ec2.yml`
- 프로덕션 네이티브 이미지는 CI에서만 빌드 (ARM Mac에서 빌드 불가)

## Database Access

- Host: `43.203.248.188`
- Port: `3306`
- Database: `book_db`
- User: `book`
- Password: `qlalfqjsgh486`
- JDBC URL: `jdbc:mysql://43.203.248.188:3306/book_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8`

로컬 개발 시 `env/.env` 파일에서 환경변수를 로딩한다.

## Test Account

- Email: `test@test.com`
- Password: `123456`

## SSH & Runtime Checks

```bash
ssh -i /Users/ian-mac/.ssh/dokseogd_prod.pem ubuntu@43.203.248.188
docker ps
docker logs reading-garden --tail 200
docker compose -f /opt/reading-garden/docker-compose.yml ps
curl http://127.0.0.1:8080/api/health
```

## Key Conventions

- Kotlin: 4-space indent, `PascalCase` 클래스, `camelCase` 멤버, lowercase 패키지
- 생성자 주입, 불변 DTO, 명시적 request/response 모델
- Conventional Commits: `feat:`, `fix:`, `test:`, `refactor:` 등
- 시크릿(DB 크레덴셜, Firebase JSON 등)은 절대 커밋하지 않음

## Tech Stack

- Kotlin 2.3.20 / Java 25 / Spring Boot 4.0.5
- Spring Security + JJWT 0.12.7
- Spring Data JPA + MySQL (H2 for tests)
- Firebase Admin SDK 9.8.0 (FCM)
- SpringDoc OpenAPI 3.0.1 (Swagger UI: `/swagger-ui.html`)
- GraalVM Native Image (buildtools-native 0.11.3)
- Paketo Buildpacks (builder-noble-java-tiny)
