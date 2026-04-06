package std.nooook.readinggardenkotlin.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.integration.AladinClient
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(LegacyApiCompatibilityTest.TestConfig::class)
class LegacyApiCompatibilityTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val fixedAladinClient: FixedAladinClient,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        userRepository.deleteAll()

        fixedAladinClient.searchBooksResponse = mapOf(
            "query" to "클린 코드",
            "startIndex" to 1,
            "itemsPerPage" to 100,
            "item" to listOf(
                mapOf(
                    "title" to "클린 코드 결과",
                    "author" to "로버트 C. 마틴",
                    "isbn13" to "9780132350884",
                    "cover" to "https://example.com/cover.jpg",
                    "publisher" to "프래그마틱",
                ),
            ),
        )
    }

    @Test
    fun `post garden create should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardencontract@example.com")

        val response = mockMvc.perform(
            post("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"새 가든","garden_info":"소개","garden_color":"blue"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/create-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get book search should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("bookcontract@example.com")

        val response = mockMvc.perform(
            get("/api/v1/book/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/book/search-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get memo list should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("memocontract@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("memocontract@example.com")?.userNo)
        val gardenNo = gardenRepository.save(
            GardenEntity(
                gardenTitle = "메모 가든",
                gardenInfo = "소개",
                gardenColor = "green",
            ),
        ).gardenNo ?: error("gardenNo was not generated")
        val bookNo = bookRepository.save(
            BookEntity(
                gardenNo = gardenNo,
                bookTitle = "책 제목",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 240,
                bookImageUrl = "https://example.com/book.jpg",
                bookInfo = "책 소개",
            ),
        ).bookNo ?: error("bookNo was not generated")

        memoRepository.save(
            MemoEntity(
                bookNo = bookNo,
                memoContent = "메모 내용",
                userNo = userNo,
                memoLike = true,
                memoCreatedAt = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0),
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/memo/list-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
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

    private fun readFixture(path: String): JsonNode =
        ClassPathResource(path).inputStream.use(objectMapper::readTree)

    private fun assertSameShape(
        fixture: JsonNode,
        actual: JsonNode,
        path: String = "$",
    ) {
        when {
            fixture.isObject -> {
                assertThat(actual.isObject).withFailMessage("$path should be an object").isTrue()
                val expectedFields = fixture.fieldNames().asSequence().toSet()
                val actualFields = actual.fieldNames().asSequence().toSet()
                assertThat(actualFields).withFailMessage("$path field keys differ").isEqualTo(expectedFields)
                expectedFields.forEach { field ->
                    assertThat(actual.has(field)).withFailMessage("$path.$field is missing").isTrue()
                    assertSameShape(fixture.get(field), actual.get(field), "$path.$field")
                }
            }

            fixture.isArray -> {
                assertThat(actual.isArray).withFailMessage("$path should be an array").isTrue()
                if (fixture.size() > 0) {
                    assertThat(actual.size()).withFailMessage("$path should contain at least one element").isGreaterThan(0)
                    assertSameShape(fixture[0], actual[0], "$path[0]")
                }
            }

            fixture.isTextual -> {
                assertThat(actual.isTextual).withFailMessage("$path should be a string").isTrue()
            }

            fixture.isNumber -> {
                assertThat(actual.isNumber).withFailMessage("$path should be a number").isTrue()
            }

            fixture.isBoolean -> {
                assertThat(actual.isBoolean).withFailMessage("$path should be a boolean").isTrue()
            }

            fixture.isNull -> {
                assertThat(actual.isNull).withFailMessage("$path should be null").isTrue()
            }

            else -> {
                assertNotNull(actual, "$path should exist")
            }
        }
    }

    class FixedAladinClient : AladinClient {
        var searchBooksResponse: Map<String, Any?> = emptyMap()

        override fun searchBooks(
            query: String,
            start: Int,
            maxResults: Int,
        ): Map<String, Any?> = searchBooksResponse

        override fun searchBookByIsbn(query: String): Map<String, Any?> = emptyMap()

        override fun getBookDetailByIsbn(query: String): Map<String, Any?> = emptyMap()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun aladinClient(): FixedAladinClient = FixedAladinClient()
    }
}
