package std.nooook.readinggardenkotlin.modules.push.controller

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "푸시 설정 수정 요청")
data class PushRequest(
    @field:Schema(description = "앱 푸시 전체 허용 여부", example = "true", nullable = true)
    val push_app_ok: Boolean?,
    @field:Schema(description = "독서 푸시 허용 여부", example = "false", nullable = true)
    val push_book_ok: Boolean?,
    @field:Schema(description = "푸시 전송 희망 시각", example = "2026-04-10T21:00:00", nullable = true)
    val push_time: LocalDateTime?,
)
