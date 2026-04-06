package std.nooook.readinggardenkotlin.modules.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.integration.AladinClient
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(BookControllerIntegrationTest.TestConfig::class)
class BookControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val recordingAladinClient: RecordingAladinClient,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
        recordingAladinClient.lastQuery = null
        recordingAladinClient.lastStart = null
        recordingAladinClient.lastMaxResults = null
        recordingAladinClient.searchIsbnResponse = mapOf(
            "item" to listOf(mapOf("isbn13" to "9788937462788")),
        )
        recordingAladinClient.detailResponse = mapOf(
            "searchCategoryId" to 1,
            "searchCategoryName" to "소설",
            "item" to listOf(
                mapOf(
                    "title" to "상세 책",
                    "author" to "저자",
                    "description" to "소개",
                    "isbn13" to "9788937462788",
                    "cover" to "https://example.com/book.jpg",
                    "publisher" to "출판사",
                    "subInfo" to mapOf("itemPage" to 321),
                ),
            ),
        )
    }

    @Test
    fun `search should require authentication`() {
        mockMvc.perform(
            get("/api/v1/book/search")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
    }

    @Test
    fun `search should use default paging values and return adapter payload`() {
        val accessToken = signupAndGetAccessToken("booksearch@example.com")

        mockMvc.perform(
            get("/api/v1/book/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색 성공"))
            .andExpect(jsonPath("$.data.query").value("클린 코드"))
            .andExpect(jsonPath("$.data.startIndex").value(1))
            .andExpect(jsonPath("$.data.itemsPerPage").value(100))
            .andExpect(jsonPath("$.data.item[0].title").value("클린 코드 결과"))

        assertEquals("클린 코드", recordingAladinClient.lastQuery)
        assertEquals(1, recordingAladinClient.lastStart)
        assertEquals(100, recordingAladinClient.lastMaxResults)
    }

    @Test
    fun `search isbn should return adapter payload`() {
        val accessToken = signupAndGetAccessToken("bookisbn@example.com")

        mockMvc.perform(
            get("/api/v1/book/search-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색(ISBN) 성공"))
            .andExpect(jsonPath("$.data.item[0].isbn13").value("9788937462788"))

        assertEquals("9788937462788", recordingAladinClient.lastIsbnQuery)
    }

    @Test
    fun `detail isbn should shape response like legacy`() {
        val accessToken = signupAndGetAccessToken("bookdetail@example.com")

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상세 조회 성공"))
            .andExpect(jsonPath("$.data.searchCategoryId").value(1))
            .andExpect(jsonPath("$.data.searchCategoryName").value("소설"))
            .andExpect(jsonPath("$.data.title").value("상세 책"))
            .andExpect(jsonPath("$.data.author").value("저자"))
            .andExpect(jsonPath("$.data.description").value("소개"))
            .andExpect(jsonPath("$.data.isbn13").value("9788937462788"))
            .andExpect(jsonPath("$.data.cover").value("https://example.com/book.jpg"))
            .andExpect(jsonPath("$.data.publisher").value("출판사"))
            .andExpect(jsonPath("$.data.itemPage").value(321))
            .andExpect(jsonPath("$.data.record").isMap)
            .andExpect(jsonPath("$.data.memo").isMap)

        assertEquals("9788937462788", recordingAladinClient.lastDetailQuery)
    }

    @Test
    fun `detail isbn should fail loudly when required fields are missing`() {
        val accessToken = signupAndGetAccessToken("bookdetailfail@example.com")
        recordingAladinClient.detailResponse = mapOf(
            "searchCategoryId" to 1,
            "searchCategoryName" to "소설",
            "item" to listOf(emptyMap<String, Any>()),
        )

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.resp_code").value(500))
    }

    private fun signupAndGetAccessToken(email: String): String {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"pw1234","user_fcm":"fcm-token","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()
    }

    class RecordingAladinClient : AladinClient {
        var lastQuery: String? = null
        var lastStart: Int? = null
        var lastMaxResults: Int? = null
        var lastIsbnQuery: String? = null
        var lastDetailQuery: String? = null
        var searchIsbnResponse: Map<String, Any?> = emptyMap()
        var detailResponse: Map<String, Any?> = emptyMap()

        override fun searchBooks(
            query: String,
            start: Int,
            maxResults: Int,
        ): Map<String, Any?> {
            lastQuery = query
            lastStart = start
            lastMaxResults = maxResults
            return mapOf(
                "query" to query,
                "startIndex" to start,
                "itemsPerPage" to maxResults,
                "item" to listOf(mapOf("title" to "${query} 결과")),
            )
        }

        override fun searchBookByIsbn(query: String): Map<String, Any?> {
            lastIsbnQuery = query
            return searchIsbnResponse
        }

        override fun getBookDetailByIsbn(query: String): Map<String, Any?> {
            lastDetailQuery = query
            return detailResponse
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun aladinClient(): RecordingAladinClient = RecordingAladinClient()
    }
}
