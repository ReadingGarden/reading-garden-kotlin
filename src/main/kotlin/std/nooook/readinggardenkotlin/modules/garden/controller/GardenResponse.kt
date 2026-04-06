package std.nooook.readinggardenkotlin.modules.garden.controller

data class CreateGardenResponse(
    val garden_no: Int,
    val garden_title: String,
    val garden_info: String,
    val garden_color: String,
)
