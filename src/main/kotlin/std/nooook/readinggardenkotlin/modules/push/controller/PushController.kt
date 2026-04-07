package std.nooook.readinggardenkotlin.modules.push.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses
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

    @PutMapping("", "/")
    fun updatePush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: PushRequest,
    ): LegacyHttpResponse {
        pushService.updatePush(
            userNo = principal.userNo.toInt(),
            push_app_ok = request.push_app_ok,
            push_book_ok = request.push_book_ok,
            push_time = request.push_time,
        )

        return LegacyResponses.ok("푸시 알림 수정 성공")
    }

    @PostMapping("/book")
    fun sendBookPush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<List<Map<String, Any>>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "독서 알림 푸시 전송 성공",
            data = pushService.sendBookPush(),
        )

    @PostMapping("/notice")
    fun sendNoticePush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam content: String,
    ): LegacyDataResponse<List<Map<String, Any>>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "공지사항 푸시 전송 성공",
            data = pushService.sendNoticePush(content),
        )
}

data class PushResponse(
    val push_app_ok: Boolean,
    val push_book_ok: Boolean,
    val push_time: LocalDateTime?,
)
