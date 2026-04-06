package std.nooook.readinggardenkotlin.modules.book.integration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@Component
class AladinRestClient(
    @Value("\${app.integrations.aladin-ttbkey:}")
    private val aladinTtbKey: String,
) : AladinClient {
    private val restClient: RestClient = RestClient.builder().build()
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    override fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): Map<String, Any?> {
        if (aladinTtbKey.isBlank()) {
            return emptySearchResponse(query, start, maxResults)
        }

        val uri = UriComponentsBuilder
            .fromUriString(ALADIN_ITEM_SEARCH_URL)
            .queryParam("ttbkey", aladinTtbKey)
            .queryParam("Query", query)
            .queryParam("QueryType", "Keyword")
            .queryParam("MaxResults", maxResults)
            .queryParam("Start", start)
            .queryParam("Cover", "Big")
            .queryParam("SearchTarget", "BOOK")
            .queryParam("output", "js")
            .queryParam("Version", "20131101")
            .build(true)
            .toUri()

        val body = restClient.get()
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String::class.java)
            ?: return emptySearchResponse(query, start, maxResults)

        return objectMapper.readValue(body, object : TypeReference<Map<String, Any?>>() {})
    }

    private fun emptySearchResponse(
        query: String,
        start: Int,
        maxResults: Int,
    ): Map<String, Any?> =
        mapOf(
            "query" to query,
            "startIndex" to start,
            "itemsPerPage" to maxResults,
            "item" to emptyList<Map<String, Any?>>(),
        )

    companion object {
        private const val ALADIN_ITEM_SEARCH_URL = "http://www.aladin.co.kr/ttb/api/ItemSearch.aspx"
    }
}
