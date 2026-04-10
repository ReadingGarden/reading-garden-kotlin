package std.nooook.readinggardenkotlin.common.api

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "LegacyHttpResponse", description = "레거시 기본 응답 envelope입니다.")
data class LegacyHttpResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "조회 성공")
    val resp_msg: String,
    @field:ArraySchema(schema = Schema(implementation = LegacyErrorDetail::class))
    @field:Schema(description = "검증 실패나 바인딩 실패가 있는 경우 포함되는 상세 오류 목록")
    val errors: List<Map<String, Any?>>? = null,
)

@Schema(name = "LegacyDataResponse", description = "레거시 데이터 응답 envelope입니다.")
data class LegacyDataResponse<T>(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "조회 성공")
    val resp_msg: String,
    @field:Schema(description = "실제 응답 데이터")
    val data: T,
)

@Schema(description = "레거시 에러 상세 항목 예시")
data class LegacyErrorDetail(
    @field:Schema(description = "문제가 발생한 요청 파라미터 이름", example = "user_email", nullable = true)
    val parameter: String? = null,
    @field:Schema(description = "문제가 발생한 필드 이름", example = "user_email", nullable = true)
    val field: String? = null,
    @field:Schema(description = "에러 메시지", example = "must match legacy email format")
    val message: String? = null,
    @field:Schema(description = "거부된 값", example = "wrong-email", nullable = true)
    val rejectedValue: Any? = null,
)
