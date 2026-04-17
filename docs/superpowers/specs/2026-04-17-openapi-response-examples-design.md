# OpenAPI Response Examples Design

## 목표

Swagger UI의 모든 공개 API 응답 예시를 현재 레거시 응답 규약과 실제 반환 타입에 맞는 JSON으로 정리한다.

핵심은 두 가지다.

- `additionalProp1` 같은 placeholder 예시를 제거한다.
- 응답 문서화 방식을 한 가지 규칙으로 통일해 이후 endpoint 추가 시 같은 문제가 반복되지 않게 한다.

## 범위

이번 작업에 포함한다.

- `auth`, `book`, `garden`, `memo`, `push`, `app`의 성공 응답 예시 정리
- 주요 오류 응답 `400`, `401`, `404` 예시 정리
- 응답 DTO의 `@Schema(example = "...")` 보강
- 비정형 응답에 대한 `@ApiResponse` + `@Content` + `@ExampleObject` 정리
- `/v3/api-docs` 기준의 OpenAPI 테스트 보강

이번 작업에서 제외한다.

- 실제 런타임 응답 구조 변경
- `Map` 기반 응답을 모두 DTO로 리팩터링
- Swagger UI 테마/화면 커스터마이징
- 전역 `OpenApiCustomizer` 또는 `OperationCustomizer` 도입

## 현재 상태

현재 프로젝트는 공통 envelope로 `LegacyHttpResponse`, `LegacyDataResponse<T>`를 사용한다.

이 구조 자체는 문제 없지만 문서화 관점에서는 아래 케이스에서 Swagger UI가 실제 예시를 잘 만들지 못한다.

- `LegacyDataResponse<Map<String, Any?>>`
- `LegacyDataResponse<List<Map<String, Any>>>`
- `LegacyDataResponse<String>`
- `LegacyHttpResponse`
- DTO 안에 다시 `Map<String, Any?>`가 포함된 경우
- 에러 응답의 `errors: List<Map<String, Any?>>`

그 결과 Swagger UI의 Example Value에 아래와 같은 placeholder가 나타난다.

```json
{
  "resp_code": 200,
  "resp_msg": "조회 성공",
  "data": {
    "additionalProp1": "string",
    "additionalProp2": "string",
    "additionalProp3": "string"
  }
}
```

반면, 일부 endpoint는 이미 `OpenApiExamples`와 `ExampleObject`를 사용해 실제 JSON 예시를 붙여 두었다. 즉 현재 코드베이스는 두 가지 방식이 섞여 있고, 그 경계가 명확하지 않다.

## 접근안

### 접근안 1. 모든 응답을 `ExampleObject`로 수동 고정

모든 성공/실패 응답에 endpoint별 JSON 문자열 예시를 직접 붙인다.

장점:

- 결과가 가장 예측 가능하다.
- Swagger UI에 보이는 JSON을 정확히 통제할 수 있다.

단점:

- 중복이 많다.
- DTO가 바뀔 때 example 문자열도 함께 관리해야 한다.

### 접근안 2. DTO example 우선, 비정형 응답만 `ExampleObject`

구체 DTO를 반환하는 응답은 DTO 필드 `@Schema(example = "...")`를 보강해서 해결하고, Swagger가 자동 예시를 잘 만들 수 없는 케이스만 `ExampleObject`를 사용한다.

장점:

- 중복이 줄어든다.
- DTO와 문서가 가까워 추적이 쉽다.
- placeholder 문제를 실질적으로 제거할 수 있다.

단점:

- `Map`, `String`, 빈 객체 응답은 여전히 수동 예시가 필요하다.
- endpoint별 `resp_msg`를 완전히 자동화하지는 못한다.

### 접근안 3. 전역 customizer로 후처리

`OpenApiCustomizer` 또는 `OperationCustomizer`에서 operation별 response 예시를 자동 주입한다.

장점:

- 이론상 annotation 중복을 줄일 수 있다.

단점:

- 현재처럼 `Map`, `String`, `LegacyHttpResponse`, 제네릭 envelope가 섞인 구조에서는 실제 `data` 형태를 안정적으로 추론하기 어렵다.
- 결국 endpoint별 매핑 테이블을 코드로 따로 유지하게 된다.
- 문서 정의가 controller/DTO에서 멀어져 가독성과 추적성이 떨어진다.

채택안은 접근안 2다.

## 설계 원칙

### 1. DTO가 있으면 DTO에 example를 둔다

`data`가 구체 DTO 또는 DTO 리스트라면, 예시의 중심은 DTO가 된다.

적용 방식:

- 각 응답 DTO 필드에 `@Schema(description = "...", example = "...")`를 보강한다.
- 컨트롤러 응답 문서는 가능하면 스키마 중심으로 유지한다.

이 원칙의 목적은 `data` 내부 예시를 DTO 정의와 함께 유지하는 것이다.

### 2. 비정형 응답은 전체 JSON example를 둔다

아래 케이스는 `ExampleObject`를 사용해 응답 본문 전체를 JSON 문자열로 명시한다.

- `LegacyHttpResponse`
- `LegacyDataResponse<Map<...>>`
- `LegacyDataResponse<List<Map<...>>>`
- `LegacyDataResponse<String>`
- 빈 `HashMap()` 또는 의미 없는 `{}`가 `data`로 들어가는 경우
- `400`, `401`, `404` 오류 응답

이 케이스는 Swagger가 구조를 자동으로 맞추기 어렵기 때문에 endpoint별 최종 JSON을 고정하는 편이 안전하다.

### 3. 전역 customizer는 도입하지 않는다

이번 작업에서는 `OpenApiCustomizer`/`OperationCustomizer`를 추가하지 않는다.

이유:

- 자동화 범위보다 예외 케이스가 더 많다.
- 현재 구조에서는 controller/DTO에 붙은 문서가 가장 읽기 쉽고 유지보수하기 쉽다.

## 컨트롤러별 적용 경계

### DTO example 중심으로 처리할 응답

- `GET /api/v1/app/version`
- `POST /api/v1/auth`
- `GET /api/v1/auth`
- `PUT /api/v1/auth`
- `GET /api/v1/garden/list`
- `GET /api/v1/garden/detail`
- `POST /api/v1/garden`
- `GET /api/v1/memo`
- `GET /api/v1/memo/detail`
- `POST /api/v1/memo`
- `GET /api/v1/push`
- `POST /api/v1/book`
- `GET /api/v1/book/status`
- `GET /api/v1/book/read`
- `POST /api/v1/book/read`

이 그룹은 `data`가 구체 DTO이므로 DTO field example 보강이 중심이다.

### `ExampleObject` 중심으로 처리할 응답

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/refresh`
- `DELETE /api/v1/auth`
- `POST /api/v1/auth/find-password`
- `POST /api/v1/auth/find-password/check`
- `PUT /api/v1/auth/find-password/update-password`
- `PUT /api/v1/auth/update-password`
- `PUT /api/v1/garden`
- `DELETE /api/v1/garden`
- `PUT /api/v1/garden/to`
- `DELETE /api/v1/garden/member`
- `PUT /api/v1/garden/member`
- `PUT /api/v1/garden/main`
- `POST /api/v1/garden/invite`
- `PUT /api/v1/memo`
- `DELETE /api/v1/memo`
- `POST /api/v1/memo/image`
- `DELETE /api/v1/memo/image`
- `PUT /api/v1/memo/like`
- `PUT /api/v1/push`
- `POST /api/v1/push/book`
- `POST /api/v1/push/notice`
- `GET /api/v1/book`
- `GET /api/v1/book/search`
- `GET /api/v1/book/search-isbn`
- `GET /api/v1/book/detail-isbn`
- `DELETE /api/v1/book`
- `PUT /api/v1/book`
- `PUT /api/v1/book/read`
- `DELETE /api/v1/book/read`
- `POST /api/v1/book/image`
- `DELETE /api/v1/book/image`

이 그룹은 `Map`, `String`, `LegacyHttpResponse`, placeholder 객체, multipart 성공 응답 등이 포함되므로 전체 JSON example가 필요하다.

`GET /api/v1/book/detail-isbn`은 `BookDetailResponse` DTO를 반환하지만 내부 `record`, `memo`가 `Map<String, Any?>`이므로 안전하게 전체 example를 유지한다.

## 파일별 변경 설계

### 1. `common/docs/OpenApiExamples.kt`

역할:

- 비정형 성공 응답 example 상수 추가
- 공통 오류 응답 example 상수 정리

원칙:

- 실제 controller의 `resp_msg`를 그대로 사용한다.
- 기존 contract fixture 또는 현재 테스트 기대값과 어긋나는 문구를 임의로 만들지 않는다.
- 예시 JSON은 최소 필드만 넣지 말고 Swagger 사용자가 바로 이해할 수 있을 정도로 완결된 형태로 작성한다.

### 2. `modules/*/controller/*Response.kt`

역할:

- DTO field example 보강
- nullable 필드, 날짜/시간 필드, 빈 배열/빈 객체 placeholder의 의도를 명확히 설명

원칙:

- example는 실제 직렬화 형식과 동일해야 한다.
- snake_case 필드는 현재 API 규약을 유지한다.

### 3. `modules/*/controller/*Controller.kt`

역할:

- DTO 중심 응답은 스키마 기반 문서화
- 비정형 응답은 `@ApiResponses`와 `ExampleObject` 연결
- 주요 오류 응답 `400/401/404`를 필요한 endpoint에 보강

원칙:

- 이미 실제 예시가 잘 달려 있는 endpoint는 유지하되 표현을 통일한다.
- success만 있는 endpoint라도 인증/입력 오류가 흔한 곳은 최소한 주요 실패 응답을 문서화한다.

### 4. `common/config/OpenApiConfigTest.kt`

역할:

- `/v3/api-docs` JSON에 기대하는 example 또는 schema가 노출되는지 검증

원칙:

- UI 스냅샷이 아니라 `/v3/api-docs`를 기준으로 검증한다.
- “특정 경로의 응답 200 example이 존재한다”, “401 example이 지정돼 있다” 같은 회귀 방지 assertion을 추가한다.

## 오류 응답 정책

오류 응답은 DTO example만으로 충분하지 않다. `errors`가 `List<Map<String, Any?>>`이기 때문이다.

따라서 다음 정책을 쓴다.

- `400`: validation/binding 실패 예시를 공통 JSON example로 제공
- `401`: 인증 실패 예시를 공통 JSON example로 제공
- `404`: 리소스 없음 또는 플랫폼 미존재 같은 대표 케이스에 대한 공통 JSON example를 제공

각 endpoint는 필요한 오류 코드만 문서화한다. 모든 endpoint에 모든 오류를 기계적으로 붙이지는 않는다.

## 검증 계획

완료 주장 전에 다음을 확인한다.

- `./gradlew test --tests '*OpenApiConfigTest'`
- 필요 시 영향 받은 controller의 MVC/OpenAPI 관련 테스트
- `/v3/api-docs`에서 아래가 보이는지 확인
  - DTO 기반 endpoint: 기대 schema와 field example
  - 비정형 endpoint: 기대 `examples`
  - 주요 오류 응답: `400`, `401`, `404` example

## 리스크와 대응

### 리스크 1. DTO example와 실제 런타임 직렬화 형식이 어긋날 수 있다

대응:

- existing DTO field명과 직렬화 규칙을 기준으로 example를 작성한다.
- 날짜/시간은 현재 테스트와 코드에서 사용하는 형식에 맞춘다.

### 리스크 2. `ExampleObject`가 늘어나면 관리 비용이 다시 커질 수 있다

대응:

- 비정형 응답에만 제한적으로 사용한다.
- 구체 DTO 응답에는 가능한 한 추가하지 않는다.

### 리스크 3. 일부 endpoint의 `resp_msg`가 controller/service 구현과 어긋날 수 있다

대응:

- controller 반환값과 기존 MVC 테스트 assertion을 기준으로 문구를 맞춘다.
- 추정 문구를 새로 만들지 않는다.

## 완료 기준

다음을 모두 만족하면 이번 설계는 완료된 것으로 본다.

- Swagger UI에서 더 이상 placeholder 중심의 응답 예시가 핵심 사용자 경로에 남지 않는다.
- DTO 기반 응답은 DTO field example만으로 이해 가능하다.
- 비정형 응답은 endpoint별 실제 JSON example가 노출된다.
- `/v3/api-docs` 테스트로 문서 회귀를 감지할 수 있다.
