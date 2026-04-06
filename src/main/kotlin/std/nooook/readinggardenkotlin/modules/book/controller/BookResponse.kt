package std.nooook.readinggardenkotlin.modules.book.controller

typealias BookSearchResponse = Map<String, Any?>

typealias BookLookupResponse = Map<String, Any?>

data class CreateBookResponse(
    val book_no: Int,
)

data class CreateReadResponse(
    val book_current_page: Int,
    val percent: Double,
)

data class BookReadHistoryItemResponse(
    val id: Int,
    val book_current_page: Int,
    val book_start_date: String?,
    val book_end_date: String?,
    val created_ad: String,
)

data class BookReadMemoItemResponse(
    val id: Int,
    val memo_content: String,
    val memo_like: Boolean,
    val memo_created_at: String,
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

data class BookReadDetailResponse(
    val user_no: Int,
    val book_title: String,
    val book_author: String,
    val book_publisher: String,
    val book_info: String,
    val book_image_url: String?,
    val book_tree: String?,
    val book_status: Int,
    val book_page: Int,
    val garden_no: Int?,
    val book_current_page: Int,
    val percent: Double,
    val book_read_list: List<BookReadHistoryItemResponse>,
    val memo_list: List<BookReadMemoItemResponse>,
)
