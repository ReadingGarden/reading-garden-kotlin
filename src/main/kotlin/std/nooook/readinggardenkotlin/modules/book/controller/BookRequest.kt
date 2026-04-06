package std.nooook.readinggardenkotlin.modules.book.controller

import java.time.LocalDateTime

data class SearchBooksRequest(
    val query: String,
    val start: Int = 1,
    val maxResults: Int = 100,
)

data class IsbnQueryRequest(
    val query: String,
)

data class CreateBookRequest(
    val book_isbn: String? = null,
    val garden_no: Int? = null,
    val book_title: String,
    val book_info: String,
    val book_author: String,
    val book_publisher: String,
    val book_tree: String? = null,
    val book_image_url: String? = null,
    val book_status: Int,
    val book_page: Int,
)

data class UpdateBookRequest(
    val garden_no: Int? = null,
    val book_tree: String? = null,
    val book_status: Int? = null,
)

data class CreateReadRequest(
    val book_no: Int,
    val book_start_date: LocalDateTime? = null,
    val book_end_date: LocalDateTime? = null,
    val book_current_page: Int,
)

data class UpdateReadRequest(
    val book_start_date: LocalDateTime? = null,
    val book_end_date: LocalDateTime? = null,
)
