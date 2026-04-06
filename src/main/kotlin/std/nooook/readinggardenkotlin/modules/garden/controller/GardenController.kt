package std.nooook.readinggardenkotlin.modules.garden.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService

@RequestMapping("/api/v1/garden")
@RestController
class GardenController(
    private val gardenService: GardenService,
) {
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
}
