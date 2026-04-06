package std.nooook.readinggardenkotlin.modules.memo.controller

data class CreateMemoResponse(
    val id: Int,
)

data class MemoListItemResponse(
    val id: Int,
    val book_no: Int,
    val book_title: String,
    val book_author: String,
    val book_image_url: String?,
    val memo_content: String,
    val memo_like: Boolean,
    val image_url: String,
    val memo_created_at: String,
)

data class MemoListResponse(
    val current_page: Int,
    val max_page: Int,
    val total: Long,
    val page_size: Int,
    val list: List<MemoListItemResponse>,
)

data class MemoDetailResponse(
    val id: Int,
    val book_no: Int,
    val book_title: String,
    val book_author: String,
    val book_publisher: String,
    val book_info: String,
    val memo_content: String,
    val image_url: String?,
    val memo_created_at: String,
)
