package std.nooook.readinggardenkotlin.modules.memo.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.memo.service.MemoService

@RequestMapping("/api/v1/memo")
@RestController
class MemoController(
    private val memoService: MemoService,
) {
    @GetMapping("", "/")
    fun getMemoList(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", required = false, defaultValue = "10") pageSize: Int,
    ): LegacyDataResponse<MemoListResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "메모 리스트 조회 성공",
            data = memoService.getMemoList(
                userNo = principal.userNo.toInt(),
                page = page,
                pageSize = pageSize,
            ),
        )
}
