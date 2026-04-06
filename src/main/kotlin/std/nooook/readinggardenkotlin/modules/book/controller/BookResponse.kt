package std.nooook.readinggardenkotlin.modules.book.controller

typealias BookSearchResponse = Map<String, Any?>

typealias BookLookupResponse = Map<String, Any?>

data class CreateBookResponse(
    val book_no: Int,
)

data class BookStatusItemResponse(
    val book_no: Int,
    val book_title: String,
    val book_author: String,
    val book_publisher: String,
    val book_info: String,
    val book_image_url: String?,
    val book_tree: String?,
    val book_status: Int,
    val percent: Double,
    val book_page: Int,
    val garden_no: Int?,
)

data class BookStatusResponse(
    val current_page: Int,
    val max_page: Int,
    val total_items: Int,
    val page_size: Int,
    val list: List<BookStatusItemResponse>,
)

data class BookDetailResponse(
    val searchCategoryId: Any,
    val searchCategoryName: String,
    val title: String,
    val author: String,
    val description: String,
    val isbn13: String,
    val cover: String,
    val publisher: String,
    val itemPage: Int,
    val record: Map<String, Any?>,
    val memo: Map<String, Any?>,
)
