package std.nooook.readinggardenkotlin.modules.garden.controller

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "가든 생성 요청")
data class CreateGardenRequest(
    @field:Schema(description = "가든 제목", example = "새 가든")
    val garden_title: String = "",
    @field:Schema(description = "가든 소개", example = "소개")
    val garden_info: String = "",
    @field:Schema(description = "가든 색상 키", example = "blue")
    val garden_color: String = "red",
)

@Schema(description = "가든 수정 요청")
data class UpdateGardenRequest(
    @field:Schema(description = "수정할 가든 제목", example = "수정된 가든")
    val garden_title: String = "",
    @field:Schema(description = "수정할 가든 소개", example = "수정된 소개")
    val garden_info: String = "",
    @field:Schema(description = "수정할 색상 키", example = "green")
    val garden_color: String = "red",
)
