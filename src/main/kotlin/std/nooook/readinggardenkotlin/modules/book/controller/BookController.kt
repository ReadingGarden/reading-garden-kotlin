package std.nooook.readinggardenkotlin.modules.book.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.modules.book.service.BookService

@RequestMapping("/api/v1/book")
@RestController
class BookController(
    private val bookService: BookService,
) {
    @GetMapping("/search")
    fun searchBooks(
        @RequestParam query: String,
        @RequestParam(required = false, defaultValue = "1") start: Int,
        @RequestParam(required = false, defaultValue = "100") maxResults: Int,
    ): LegacyDataResponse<BookSearchResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 검색 성공",
            data = bookService.searchBooks(
                query = query,
                start = start,
                maxResults = maxResults,
            ),
        )

    @GetMapping("/search-isbn")
    fun searchBookByIsbn(
        @RequestParam query: String,
    ): LegacyDataResponse<BookLookupResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 검색(ISBN) 성공",
            data = bookService.searchBookByIsbn(query),
        )

    @GetMapping("/detail-isbn")
    fun getBookDetailByIsbn(
        @RequestParam query: String,
    ): LegacyDataResponse<BookDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 상세 조회 성공",
            data = bookService.getBookDetailByIsbn(query),
        )
}
