package std.nooook.readinggardenkotlin.modules.garden.controller

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
import std.nooook.readinggardenkotlin.modules.garden.service.GardenCommandService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenMembershipService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenQueryService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService

@RequestMapping("/api/v1/garden")
@RestController
class GardenController(
    private val gardenService: GardenService,
    private val gardenCommandService: GardenCommandService,
    private val gardenMembershipService: GardenMembershipService,
    private val gardenQueryService: GardenQueryService,
) {
    @GetMapping("/list")
    fun getGardenList(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<List<GardenListItemResponse>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "가든 리스트 조회 성공",
            data = gardenQueryService.getGardenList(principal.userNo.toInt()),
        )

    @GetMapping("/detail")
    fun getGardenDetail(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyDataResponse<GardenDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "가든 상세 조회 성공",
            data = gardenQueryService.getGardenDetail(principal.userNo.toInt(), gardenNo),
        )

    @PostMapping("", "/")
    fun createGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateGardenRequest,
    ): ResponseEntity<LegacyDataResponse<CreateGardenResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "가든 추가 성공",
                data = gardenService.createGarden(principal.userNo.toInt(), request),
            ),
        )

    @PutMapping("", "/")
    fun updateGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
        @RequestBody request: UpdateGardenRequest,
    ): LegacyDataResponse<Map<String, Any?>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = gardenCommandService.updateGarden(principal.userNo.toInt(), gardenNo, request),
            data = emptyMap<String, Any?>(),
        )

    @DeleteMapping("", "/")
    fun deleteGarden(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.deleteGarden(principal.userNo.toInt(), gardenNo),
        )

    @PutMapping("/to")
    fun moveGardenBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
        @RequestParam(name = "to_garden_no") toGardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.moveGardenBook(principal.userNo.toInt(), gardenNo, toGardenNo),
        )

    @DeleteMapping("/member")
    fun leaveGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenMembershipService.leaveGardenMember(principal.userNo.toInt(), gardenNo),
        )

    @PutMapping("/member")
    fun updateGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
        @RequestParam(name = "user_no") userNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenMembershipService.updateGardenMember(principal.userNo.toInt(), gardenNo, userNo),
        )

    @PutMapping("/main")
    fun updateGardenMain(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            gardenCommandService.updateGardenMain(principal.userNo.toInt(), gardenNo),
        )

    @PostMapping("/invite")
    fun inviteGardenMember(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no") gardenNo: Int,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = gardenMembershipService.inviteGardenMember(principal.userNo.toInt(), gardenNo),
            ),
        )
}
