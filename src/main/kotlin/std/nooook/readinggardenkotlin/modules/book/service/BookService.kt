package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.book.controller.BookSearchResponse
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
}
