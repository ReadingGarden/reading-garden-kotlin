package std.nooook.readinggardenkotlin.modules.app.controller

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "앱 버전 조회 응답 데이터")
data class AppVersionResponse(
    @field:Schema(description = "플랫폼", example = "ios")
    val platform: String,
    @field:Schema(description = "최신 버전", example = "1.2.0")
    val latest_version: String,
    @field:Schema(description = "최소 지원 버전", example = "1.0.0")
    val min_supported_version: String,
    @field:Schema(description = "스토어 URL", example = "https://apps.apple.com/app/id1234567890")
    val store_url: String,
)

@Schema(name = "AppVersionLegacyDataResponse", description = "앱 버전 조회 성공 레거시 envelope")
data class AppVersionLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "앱 버전 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "앱 버전 응답 데이터")
    val data: AppVersionResponse,
)
