package std.nooook.readinggardenkotlin.modules.garden.controller

data class CreateGardenResponse(
    val garden_no: Int,
    val garden_title: String,
    val garden_info: String,
    val garden_color: String,
)

data class GardenListMemberResponse(
    val user_no: Int,
    val user_nick: String,
    val user_image: String,
    val garden_leader: Boolean,
    val garden_sign_date: String,
)

data class GardenListItemResponse(
    val garden_no: Int,
    val garden_title: String,
    val garden_info: String,
    val garden_color: String,
    val garden_members: Int,
    val book_count: Int,
    val garden_created_at: String,
)

data class GardenDetailBookResponse(
    val book_no: Int,
    val book_isbn: String?,
    val book_title: String,
    val book_author: String,
    val book_publisher: String,
    val book_info: String,
    val book_image_url: String?,
    val book_tree: String?,
    val book_status: Int,
    val percent: Double,
    val user_no: Int,
    val book_page: Int,
)

data class GardenDetailResponse(
    val garden_no: Int,
    val garden_title: String,
    val garden_info: String,
    val garden_color: String,
    val garden_created_at: String,
    val book_list: List<GardenDetailBookResponse>,
    val garden_members: List<GardenListMemberResponse>,
)
