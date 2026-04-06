package std.nooook.readinggardenkotlin.modules.push.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import java.time.LocalDateTime

@RequestMapping("/api/v1/push")
@RestController
class PushController(
    private val pushService: PushService,
) {
    @GetMapping("", "/")
    fun getPush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<PushResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "푸시 알림 조회 성공",
            data = pushService.getPush(principal.userNo.toInt()),
        )
}

data class PushResponse(
    val push_app_ok: Boolean,
    val push_book_ok: Boolean,
    val push_time: LocalDateTime?,
)
