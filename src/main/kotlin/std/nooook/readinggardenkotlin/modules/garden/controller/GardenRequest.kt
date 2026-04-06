package std.nooook.readinggardenkotlin.modules.garden.controller

data class CreateGardenRequest(
    val garden_title: String = "",
    val garden_info: String = "",
    val garden_color: String = "red",
)

data class UpdateGardenRequest(
    val garden_title: String = "",
    val garden_info: String = "",
    val garden_color: String = "red",
)
