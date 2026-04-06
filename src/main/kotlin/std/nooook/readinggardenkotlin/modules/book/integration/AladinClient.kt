package std.nooook.readinggardenkotlin.modules.book.integration

interface AladinClient {
    fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): Map<String, Any?>
}
