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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.integration.AladinClient
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
@Import(BookControllerIntegrationTest.TestConfig::class)
class BookControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val recordingAladinClient: RecordingAladinClient,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookRepository.deleteAll()
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

    @Test
    fun `duplication should return forbidden for existing isbn`() {
        val accessToken = signupAndGetAccessToken("dup@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("dup@example.com")?.userNo)
        bookRepository.save(
            BookEntity(
                gardenNo = null,
                bookTitle = "기존 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 2,
                userNo = userNo,
                bookPage = 120,
                bookIsbn = "9788937462788",
                bookInfo = "소개",
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("isbn", "9788937462788"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("책 중복"))
    }

    @Test
    fun `create should persist book and return created payload`() {
        val accessToken = signupAndGetAccessToken("create@example.com")
        val gardenNo = gardenRepository.save(
            GardenEntity(
                gardenTitle = "책 가든",
                gardenInfo = "소개",
                gardenColor = "blue",
            ),
        ).gardenNo ?: error("gardenNo was not generated")

        val response = mockMvc.perform(
            post("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_isbn":"9788937462788","garden_no":$gardenNo,"book_title":"새 책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_tree":"seed","book_image_url":"https://example.com/book.jpg","book_status":2,"book_page":300}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 등록 성공"))
            .andReturn()

        val bookNo = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("book_no")
            .asInt()
        val savedBook = checkNotNull(bookRepository.findById(bookNo).orElse(null))

        assertEquals(gardenNo, savedBook.gardenNo)
        assertEquals("새 책", savedBook.bookTitle)
        assertEquals("저자", savedBook.bookAuthor)
        assertEquals("출판사", savedBook.bookPublisher)
        assertEquals("seed", savedBook.bookTree)
        assertEquals("https://example.com/book.jpg", savedBook.bookImageUrl)
        assertEquals(2, savedBook.bookStatus)
        assertEquals(300, savedBook.bookPage)
        assertEquals("9788937462788", savedBook.bookIsbn)
    }

    @Test
    fun `create should reject when null garden books already reached legacy limit`() {
        val accessToken = signupAndGetAccessToken("createlimit@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("createlimit@example.com")?.userNo)
        repeat(30) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = null,
                    bookTitle = "기존 책 $index",
                    bookAuthor = "저자",
                    bookPublisher = "출판사",
                    bookStatus = 2,
                    userNo = userNo,
                    bookPage = 120,
                    bookInfo = "소개",
                ),
            )
        }

        mockMvc.perform(
            post("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_isbn":"9788937462789","book_title":"새 책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_tree":"seed","book_image_url":"https://example.com/book.jpg","book_status":2,"book_page":300}""",
                ),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("책 생성 개수 초과"))
    }

    @Test
    fun `update should mutate the targeted book and return legacy message`() {
        val accessToken = signupAndGetAccessToken("update@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("update@example.com")?.userNo)
        val sourceGardenNo = gardenRepository.save(
            GardenEntity(
                gardenTitle = "원본 가든",
                gardenInfo = "소개",
                gardenColor = "green",
            ),
        ).gardenNo ?: error("source gardenNo was not generated")
        val targetGardenNo = gardenRepository.save(
            GardenEntity(
                gardenTitle = "목표 가든",
                gardenInfo = "소개",
                gardenColor = "yellow",
            ),
        ).gardenNo ?: error("target gardenNo was not generated")
        val savedBookNo = bookRepository.save(
            BookEntity(
                gardenNo = sourceGardenNo,
                bookTitle = "수정 전",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 2,
                userNo = userNo,
                bookPage = 200,
                bookInfo = "소개",
            ),
        ).bookNo ?: error("bookNo was not generated")

        mockMvc.perform(
            put("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", savedBookNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_no":$targetGardenNo,"book_tree":"새싹","book_status":1}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 수정 성공"))

        val savedBook = checkNotNull(bookRepository.findById(savedBookNo).orElse(null))
        assertEquals(targetGardenNo, savedBook.gardenNo)
        assertEquals("새싹", savedBook.bookTree)
        assertEquals(1, savedBook.bookStatus)
    }

    @Test
    fun `status should include reading and read books when status is three`() {
        val accessToken = signupAndGetAccessToken("status@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("status@example.com")?.userNo)
        val now = LocalDateTime.of(2026, 4, 6, 12, 0, 0)
        val readingBookNo = bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "읽는중",
                bookAuthor = "a",
                bookPublisher = "p",
                bookInfo = "i",
                bookStatus = 0,
                bookPage = 100,
            ),
        ).bookNo ?: error("reading bookNo was not generated")
        val readBookNo = bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "읽음",
                bookAuthor = "a",
                bookPublisher = "p",
                bookInfo = "i",
                bookStatus = 1,
                bookPage = 100,
            ),
        ).bookNo ?: error("read bookNo was not generated")
        bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "읽고싶음",
                bookAuthor = "a",
                bookPublisher = "p",
                bookInfo = "i",
                bookStatus = 2,
                bookPage = 100,
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                bookNo = readingBookNo,
                bookCurrentPage = 20,
                createdAt = now.minusHours(2),
                userNo = userNo,
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                bookNo = readingBookNo,
                bookCurrentPage = 40,
                createdAt = now.minusHours(1),
                userNo = userNo,
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                bookNo = readBookNo,
                bookCurrentPage = 100,
                createdAt = now,
                userNo = userNo,
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "3")
                .queryParam("page", "1")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상태 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total_items").value(2))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(2))
            .andReturn()

        val list = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("list")
        val titles = list.map { it.path("book_title").asText() }.toSet()
        val percentByTitle = list.associate { it.path("book_title").asText() to it.path("percent").asDouble() }

        assertEquals(setOf("읽는중", "읽음"), titles)
        assertEquals(40.0, percentByTitle.getValue("읽는중"))
        assertEquals(100.0, percentByTitle.getValue("읽음"))
        assertFalse(titles.contains("읽고싶음"))
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
