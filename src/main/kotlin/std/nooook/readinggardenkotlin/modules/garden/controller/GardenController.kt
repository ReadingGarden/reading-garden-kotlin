package std.nooook.readinggardenkotlin.modules.garden.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.garden.service.GardenCommandService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenMembershipService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenQueryService
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RequestMapping("/api/v1/garden")
@RestController
@Tag(name = "Garden", description = "가든 조회, 생성, 수정, 이동, 멤버 관리")
class GardenController(
    private val gardenCommandService: GardenCommandService,
    private val gardenMembershipService: GardenMembershipService,
    private val gardenQueryService: GardenQueryService,
) {
    @GetMapping("/list")
    @Operation(summary = "가든 목록 조회", description = "현재 사용자가 속한 가든 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "가든 리스트 조회 성공",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.GARDEN_LIST_SUCCESS)])],
            ),
        ],
    )
    fun getGardenList(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<List<GardenListItemResponse>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "가든 리스트 조회 성공",
            data = gardenQueryService.getGardenList(principal.userNo.toInt()),
        )

    @GetMapping("/detail")
    @Operation(summary = "가든 상세 조회", description = "`garden_no`에 해당하는 가든과 책/멤버 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun getGardenDetail(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "조회할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyDataResponse<GardenDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "가든 상세 조회 성공",
            data = gardenQueryService.getGardenDetail(principal.userNo.toInt(), gardenNo),
        )

    @PostMapping("")
    @Operation(
        summary = "가든 생성",
        description = "새 가든을 생성합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateGardenRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    fun createGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateGardenRequest,
    ): ResponseEntity<LegacyDataResponse<CreateGardenResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "가든 추가 성공",
                data = gardenCommandService.createGarden(principal.userNo.toInt(), request),
            ),
        )

    @PutMapping("")
    @Operation(
        summary = "가든 수정",
        description = "`garden_no`에 해당하는 가든 제목, 소개, 색상을 수정합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateGardenRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    fun updateGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "수정할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
        @RequestBody request: UpdateGardenRequest,
    ): LegacyDataResponse<Map<String, Any?>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = gardenCommandService.updateGarden(principal.userNo.toInt(), gardenNo, request),
            data = emptyMap<String, Any?>(),
        )

    @DeleteMapping("")
    @Operation(summary = "가든 삭제", description = "`garden_no`에 해당하는 가든을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun deleteGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "삭제할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.deleteGarden(principal.userNo.toInt(), gardenNo),
        )

    @PutMapping("/to")
    @Operation(summary = "가든 간 책 이동", description = "한 가든의 책을 다른 가든으로 이동합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun moveGardenBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "현재 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
        @Parameter(description = "이동할 대상 가든 번호", example = "11")
        @RequestParam(name = "to_garden_no") toGardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.moveGardenBook(principal.userNo.toInt(), gardenNo, toGardenNo),
        )

    @DeleteMapping("/member")
    @Operation(summary = "가든 나가기", description = "현재 사용자가 `garden_no` 가든에서 탈퇴합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun leaveGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "탈퇴할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenMembershipService.leaveGardenMember(principal.userNo.toInt(), gardenNo),
        )

    @PutMapping("/member")
    @Operation(summary = "가든 리더 변경", description = "`user_no`에게 가든 리더 권한을 넘깁니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun updateGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "대상 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
        @Parameter(description = "새 리더 사용자 번호", example = "2")
        @RequestParam(name = "user_no") userNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenMembershipService.updateGardenMember(principal.userNo.toInt(), gardenNo, userNo),
        )

    @PutMapping("/main")
    @Operation(summary = "대표 가든 변경", description = "`garden_no`를 대표 가든으로 설정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun updateGardenMain(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "대표로 설정할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.updateGardenMain(principal.userNo.toInt(), gardenNo),
        )

    @PostMapping("/invite")
    @Operation(summary = "가든 초대 링크 생성", description = "`garden_no`에 대한 초대 정보를 생성합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun inviteGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "초대할 가든 번호", example = "10")
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = gardenMembershipService.inviteGardenMember(principal.userNo.toInt(), gardenNo),
            ),
        )
}
