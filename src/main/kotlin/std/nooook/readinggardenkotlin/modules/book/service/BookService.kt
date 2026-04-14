package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.BookDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookLookupResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookSearchResponse
import std.nooook.readinggardenkotlin.modules.book.controller.IsbnQueryRequest
import std.nooook.readinggardenkotlin.modules.book.controller.SearchBooksRequest
import std.nooook.readinggardenkotlin.modules.book.integration.AladinClient

@Service
class BookService(
    private val aladinClient: AladinClient,
) {
    fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): BookSearchResponse =
        aladinClient.searchBooks(
            query = SearchBooksRequest(query = query, start = start, maxResults = maxResults).query,
            start = start,
            maxResults = maxResults,
        )

    fun searchBookByIsbn(query: String): BookLookupResponse =
        aladinClient.searchBookByIsbn(
            query = IsbnQueryRequest(query = query).query,
        )

    fun getBookDetailByIsbn(query: String): BookDetailResponse {
        val response = aladinClient.getBookDetailByIsbn(
            query = IsbnQueryRequest(query = query).query,
        )

        val item = (response["item"] as? List<*>)?.firstOrNull() as? Map<*, *>
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "알라딘 API 응답에 도서 정보가 없습니다.")
        val subInfo = item["subInfo"] as? Map<*, *>

        return BookDetailResponse(
            searchCategoryId = response["searchCategoryId"] ?: 0,
            searchCategoryName = (response["searchCategoryName"] as? String).orEmpty(),
            title = (item["title"] as? String).orEmpty(),
            author = (item["author"] as? String).orEmpty(),
            description = (item["description"] as? String).orEmpty(),
            isbn13 = (item["isbn13"] as? String).orEmpty(),
            cover = (item["cover"] as? String).orEmpty(),
            publisher = (item["publisher"] as? String).orEmpty(),
            itemPage = (subInfo?.get("itemPage") as? Number)?.toInt() ?: 0,
            record = emptyMap(),
            memo = emptyMap(),
        )
    }
}
