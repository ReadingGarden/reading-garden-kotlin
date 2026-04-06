package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.stereotype.Service
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

        val item = response.requireList("item")
            .firstOrNull() as? Map<*, *>
            ?: throw IllegalStateException("Aladin detail response item is missing")
        val subInfo = item["subInfo"] as? Map<*, *>
            ?: throw IllegalStateException("Aladin detail response subInfo is missing")

        return BookDetailResponse(
            searchCategoryId = response.requireValue("searchCategoryId"),
            searchCategoryName = response.requireString("searchCategoryName"),
            title = item.requireFieldString("title"),
            author = item.requireFieldString("author"),
            description = item.requireFieldString("description"),
            isbn13 = item.requireFieldString("isbn13"),
            cover = item.requireFieldString("cover"),
            publisher = item.requireFieldString("publisher"),
            itemPage = subInfo.requireInt("itemPage"),
            record = emptyMap(),
            memo = emptyMap(),
        )
    }

    private fun Map<String, Any?>.requireValue(key: String): Any =
        this[key] ?: throw IllegalStateException("Aladin response missing $key")

    private fun Map<String, Any?>.requireString(key: String): String =
        this[key] as? String ?: throw IllegalStateException("Aladin response missing $key")

    private fun Map<String, Any?>.requireList(key: String): List<*> =
        this[key] as? List<*> ?: throw IllegalStateException("Aladin response missing $key")

    private fun Map<*, *>.requireFieldString(key: String): String =
        this[key] as? String ?: throw IllegalStateException("Aladin response missing $key")

    private fun Map<*, *>.requireInt(key: String): Int =
        when (val value = this[key]) {
            is Int -> value
            is Number -> value.toInt()
            else -> throw IllegalStateException("Aladin response missing $key")
        }
}
