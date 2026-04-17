package std.nooook.readinggardenkotlin.modules.push.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RequestMapping("/api/v1/push")
@RestController
@Tag(name = "Push", description = "푸시 설정 조회 및 수정, 운영성 푸시 전송")
class PushController(
    private val pushService: PushService,
) {
    @GetMapping("")
    @Operation(summary = "푸시 설정 조회", description = "현재 사용자의 푸시 허용 여부와 예약 시각을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "푸시 알림 조회 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PushLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.PUSH_GET_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun getPush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<PushResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "푸시 알림 조회 성공",
            data = pushService.getPush(principal.userId),
        )

    @PutMapping("")
    @Operation(
        summary = "푸시 설정 수정",
        description = "앱 푸시 허용 여부, 독서 푸시 허용 여부, 푸시 시각을 수정합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = PushRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "푸시 알림 수정 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.PUSH_UPDATE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun updatePush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: PushRequest,
    ): LegacyHttpResponse {
        pushService.updatePush(
            userId = principal.userId,
            push_app_ok = request.push_app_ok,
            push_book_ok = request.push_book_ok,
            push_time = request.push_time,
        )

        return LegacyResponses.ok("푸시 알림 수정 성공")
    }

    @PostMapping("/book")
    @Operation(summary = "독서 푸시 즉시 전송", description = "운영성 endpoint로, 독서 푸시 대상 사용자에게 즉시 푸시를 전송합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "독서 푸시 즉시 전송 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PushSendLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.PUSH_BOOK_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun sendBookPush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<List<Map<String, Any>>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "독서 알림 푸시 전송 성공",
            data = pushService.sendBookPush(),
        )

    @PostMapping("/notice")
    @Operation(summary = "공지 푸시 전송", description = "입력한 `content`를 공지 푸시로 전송합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "공지 푸시 전송 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PushSendLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.PUSH_NOTICE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun sendNoticePush(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "공지 푸시 내용", example = "오늘 저녁 9시에 점검이 있습니다.")
        @RequestParam content: String,
    ): LegacyDataResponse<List<Map<String, Any>>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "공지사항 푸시 전송 성공",
            data = pushService.sendNoticePush(content),
        )
}

@Schema(description = "푸시 설정 조회 응답 데이터")
data class PushResponse(
    @field:Schema(description = "사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "앱 푸시 전체 허용 여부", example = "true")
    val push_app_ok: Boolean,
    @field:Schema(description = "독서 푸시 허용 여부", example = "false")
    val push_book_ok: Boolean,
    @field:Schema(description = "예약 시각", example = "2026-04-10T21:00:00", nullable = true)
    val push_time: LocalDateTime?,
)

@Schema(description = "푸시 전송 결과 항목")
data class PushSendResultDocument(
    @field:Schema(description = "FCM 전송 대상 토큰", example = "fcm-token-value")
    val token: String,
    @field:Schema(description = "전송 결과", example = "sent")
    val result: String,
    @field:Schema(description = "FCM message id", example = "message-1", nullable = true)
    val message_id: String? = null,
    @field:Schema(description = "전송 실패 코드", example = "UNREGISTERED", nullable = true)
    val error_code: String? = null,
    @field:Schema(description = "전송 실패 메시지", example = "registration token is invalid", nullable = true)
    val error: String? = null,
)

@Schema(name = "PushLegacyDataResponse", description = "푸시 설정 조회 성공 레거시 envelope")
data class PushLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "푸시 알림 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "푸시 설정 데이터")
    val data: PushResponse,
)

@Schema(name = "PushSendLegacyDataResponse", description = "푸시 전송 성공 레거시 envelope")
data class PushSendLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "공지사항 푸시 전송 성공")
    val resp_msg: String,
    @field:Schema(description = "푸시 전송 결과 목록")
    val data: List<PushSendResultDocument>,
)
