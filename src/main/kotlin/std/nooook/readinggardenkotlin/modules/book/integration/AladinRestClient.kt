package std.nooook.readinggardenkotlin.modules.book.integration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.http.HttpClient
import java.time.Duration

@Component
class AladinRestClient(
    @Value("\${app.integrations.aladin-ttbkey:}")
    private val aladinTtbKey: String,
) : AladinClient, InitializingBean {
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build(),
            ).apply {
                setReadTimeout(READ_TIMEOUT)
            },
        )
        .build()
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    override fun afterPropertiesSet() {
        require(aladinTtbKey.isNotBlank()) { "app.integrations.aladin-ttbkey must not be blank" }
    }

    override fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): Map<String, Any?> {
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
            ?: return emptyMap()

        return objectMapper.readValue(body, object : TypeReference<Map<String, Any?>>() {})
    }

    companion object {
        private const val ALADIN_ITEM_SEARCH_URL = "http://www.aladin.co.kr/ttb/api/ItemSearch.aspx"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
