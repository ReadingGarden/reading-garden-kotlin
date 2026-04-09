# Repository Guidelines

## Project Structure & Migration Scope
This repository is the Kotlin/Spring Boot `4.0.5` migration target for the legacy Python backend in `back/`. Keep shared infrastructure in `common` and business code in `modules/<domain>` such as `modules/auth`, `modules/book`, `modules/garden`, `modules/memo`, `modules/push`, and `modules/scheduler`. Put Kotlin sources in `src/main/kotlin/std/nooook/readinggardenkotlin`, tests in `src/test/kotlin/std/nooook/readinggardenkotlin`, and configuration in `src/main/resources/application.yaml`. Use `back/` only to match legacy behavior and schema.

## Build, Test, and Image Commands
Use the Gradle wrapper only.

- `./gradlew bootRun`: run locally on the JVM.
- `./gradlew test`: run JUnit 5 and Spring Boot tests.
- `./gradlew build`: compile, test, and package.
- `./gradlew bootBuildImage`: build the local JVM Docker image on the host platform.
- `./gradlew bootBuildNativeAmd64Image`: build the production `linux/amd64` native image.
- `./gradlew clean`: remove `build/`.

Develop on macOS with `bootRun` or `bootBuildImage`. Build production native images in CI, not on an ARM Mac or on the EC2 host.

## Deployment Target & CI/CD
Production runs on AWS EC2 `t2.micro` with Ubuntu `24.04` amd64. Pushes to `main` trigger GitHub Actions to test, build a native image, publish to GHCR, and deploy over SSH. The image is expected to be public in GHCR so EC2 can pull it without `docker login`.

- Workflow: `.github/workflows/native-amd64-image.yml`
- Runtime manifest: `deploy/docker-compose.ec2.yml`
- Remote app dir: `/opt/reading-garden`
- Runtime env file: `/opt/reading-garden/.env`
- Firebase key file: `/opt/reading-garden/secrets/firebase-service-account.json`

## SSH & Runtime Checks
Typical access:

- `ssh -i /Users/ian-mac/.ssh/dokseogd_prod.pem ubuntu@43.203.248.188`
- `docker ps`
- `docker logs reading-garden --tail 200`
- `docker compose -f /opt/reading-garden/docker-compose.yml ps`
- `curl http://127.0.0.1:8080/api/health`

## Schema & Entity Rules
Match the legacy MySQL DDL exactly before enabling strict validation. This schema uses uppercase table names such as `BOOK`, `USER`, and `MEMO`, so preserve explicit `@Table(name = "...")` mappings and keep Hibernate’s physical naming strategy on `PhysicalNamingStrategyStandardImpl`. For binary columns, match the real MySQL type explicitly, for example `job_state blob`.

## Coding, Testing, and Commits
Follow Kotlin conventions: 4-space indentation, `PascalCase` classes, `camelCase` members, lowercase packages. Prefer constructor injection, immutable DTOs, and explicit request/response models. Add tests for migrated behavior, especially auth, validation, and persistence parity with `back/`. Use Conventional Commits such as `feat: migrate book endpoints`, `fix: align scheduler blob mapping`, and `test: add auth controller coverage`.

## Security & Configuration
Never commit secrets, DB credentials, or Firebase JSON. Keep sensitive values in `/opt/reading-garden/.env` or mounted files outside Git.

## Flutter App Compatibility
The Flutter app repository used for compatibility checks is located at `external/ReadingGarden-App`.

- When backend behavior changes, do not stop at server-only verification.
- Verify compatibility against the Flutter app's actual API usage, including routes, HTTP methods, query parameters, auth headers, response field names, and image paths.
- If claiming the Kotlin backend works as a cutover target, include app-based verification or clearly state what app-level behavior remains unverified.
