package std.nooook.readinggardenkotlin.modules.book.controller

typealias BookSearchResponse = Map<String, Any?>

typealias BookLookupResponse = Map<String, Any?>

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
