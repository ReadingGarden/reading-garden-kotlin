package std.nooook.readinggardenkotlin.modules.book.controller

data class SearchBooksRequest(
    val query: String,
    val start: Int = 1,
    val maxResults: Int = 100,
)

data class IsbnQueryRequest(
    val query: String,
)
