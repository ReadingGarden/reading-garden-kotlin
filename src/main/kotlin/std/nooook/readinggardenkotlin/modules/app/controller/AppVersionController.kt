package std.nooook.readinggardenkotlin.modules.app.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.modules.app.service.AppVersionQueryService

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App", description = "앱 레벨 메타 정보 (버전 체크 등)")
class AppVersionController(
    private val appVersionQueryService: AppVersionQueryService,
) {
    @GetMapping("/version")
    @Operation(
        summary = "앱 버전 조회",
        description = "플랫폼별 최신 버전, 최소 지원 버전, 스토어 URL을 반환합니다. 인증 불필요.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "앱 버전 조회 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AppVersionLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.APP_VERSION_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "platform 파라미터가 누락되었거나 허용되지 않은 값",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.APP_VERSION_BAD_REQUEST)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "해당 플랫폼의 앱 버전 정보가 존재하지 않음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.APP_VERSION_NOT_FOUND)],
                    ),
                ],
            ),
        ],
    )
    fun getAppVersion(
        @Parameter(description = "플랫폼", example = "ios", required = true)
        @RequestParam platform: String,
    ): LegacyDataResponse<AppVersionResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "앱 버전 조회 성공",
            data = appVersionQueryService.getByPlatform(platform),
        )
}
