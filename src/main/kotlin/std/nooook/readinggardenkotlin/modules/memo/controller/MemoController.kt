package std.nooook.readinggardenkotlin.modules.memo.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.memo.service.MemoCommandService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoQueryService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoService

@RequestMapping("/api/v1/memo")
@RestController
class MemoController(
    private val memoService: MemoService,
    private val memoQueryService: MemoQueryService,
    private val memoCommandService: MemoCommandService,
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

    @GetMapping("/detail")
    fun getMemoDetail(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
    ): LegacyDataResponse<MemoDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "메모 상세 조회 성공",
            data = memoQueryService.getMemoDetail(
                userNo = principal.userNo.toInt(),
                id = id,
            ),
        )

    @PostMapping("", "/")
    fun createMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateMemoRequest,
    ): ResponseEntity<LegacyDataResponse<CreateMemoResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "메모 추가 성공",
                data = memoCommandService.createMemo(
                    userNo = principal.userNo.toInt(),
                    request = request,
                ),
            ),
        )

    @PutMapping("", "/")
    fun updateMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
        @RequestBody request: UpdateMemoRequest,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.updateMemo(
                userNo = principal.userNo.toInt(),
                id = id,
                request = request,
            ),
        )

    @DeleteMapping("", "/")
    fun deleteMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.deleteMemo(
                userNo = principal.userNo.toInt(),
                id = id,
            ),
        )

    @PutMapping("/like")
    fun toggleMemoLike(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.toggleMemoLike(
                userNo = principal.userNo.toInt(),
                id = id,
            ),
        )
}
