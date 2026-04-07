package std.nooook.readinggardenkotlin.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
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
import java.time.LocalDateTime
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
    private val exactEnvelopeValuePaths = setOf("$.resp_code", "$.resp_msg")

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
    fun `get garden list should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenlist@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenlist@example.com")?.userNo)
        removeSignupGarden(userNo)

        val mainGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "메인 가든",
                gardenInfo = "첫 번째",
                gardenColor = "green",
                gardenCreatedAt = LocalDateTime.of(2024, 1, 5, 8, 30, 0),
            ),
        )
        val secondaryGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "서브 가든",
                gardenInfo = "두 번째",
                gardenColor = "blue",
                gardenCreatedAt = LocalDateTime.of(2024, 1, 10, 9, 0, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(mainGarden.gardenNo),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 1, 6, 7, 0, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(secondaryGarden.gardenNo),
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2024, 1, 11, 10, 0, 0),
            ),
        )
        bookRepository.save(
            BookEntity(
                gardenNo = checkNotNull(mainGarden.gardenNo),
                bookTitle = "메인 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 100,
                bookIsbn = "9781111111111",
                bookTree = "소설",
                bookImageUrl = "https://example.com/main.jpg",
                bookInfo = "메인 책 소개",
            ),
        )
        bookRepository.save(
            BookEntity(
                gardenNo = checkNotNull(secondaryGarden.gardenNo),
                bookTitle = "서브 책",
                bookAuthor = "저자2",
                bookPublisher = "출판사2",
                bookStatus = 0,
                userNo = userNo,
                bookPage = 200,
                bookIsbn = "9782222222222",
                bookTree = "에세이",
                bookImageUrl = "https://example.com/sub.jpg",
                bookInfo = "서브 책 소개",
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/garden/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/list-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get garden detail should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardendetail@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardendetail@example.com")?.userNo)
        removeSignupGarden(userNo)

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "상세 가든",
                gardenInfo = "상세 설명",
                gardenColor = "orange",
                gardenCreatedAt = LocalDateTime.of(2024, 2, 1, 12, 0, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 2, 1, 12, 5, 0),
            ),
        )
        val member = userRepository.save(
            UserEntity(
                userEmail = "gardendetail-member@example.com",
                userPassword = "pw1234",
                userCreatedAt = LocalDateTime.of(2024, 2, 1, 12, 10, 0),
                userNick = "멤버",
                userImage = "member.png",
                userFcm = "fcm-member",
                userSocialId = "",
                userSocialType = "",
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                userNo = checkNotNull(member.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2024, 2, 2, 9, 0, 0),
            ),
        )
        bookRepository.save(
            BookEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                bookTitle = "가든 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 400,
                bookIsbn = "9783333333333",
                bookTree = "인문",
                bookImageUrl = "https://example.com/detail.jpg",
                bookInfo = "가든 책 소개",
            ),
        )
        bookRepository.save(
            BookEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                bookTitle = "보조 책",
                bookAuthor = "작가2",
                bookPublisher = "출판사2",
                bookStatus = 0,
                userNo = userNo,
                bookPage = 0,
                bookIsbn = null,
                bookTree = null,
                bookImageUrl = null,
                bookInfo = "보조 책 소개",
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.gardenNo).toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/detail-success.json"),
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

    @Test
    fun `assertSameShape should enforce exact equality for legacy envelope code and message`() {
        val fixture = objectMapper.readTree(
            """{"resp_code":200,"resp_msg":"성공","data":{"dynamic_value":"fixture"}}""",
        )
        val actual = objectMapper.readTree(
            """{"resp_code":201,"resp_msg":"다름","data":{"dynamic_value":"actual"}}""",
        )

        assertThrows<AssertionError> {
            assertSameShape(fixture = fixture, actual = actual)
        }
    }

    @Test
    fun `put garden update should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenupdate@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenupdate@example.com")?.userNo)
        removeSignupGarden(userNo)

        val gardenNo = createGardenMembership(
            userNo = userNo,
            title = "수정 전 가든",
            info = "수정 전 소개",
            color = "blue",
            leader = true,
            main = true,
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"수정 후 가든","garden_info":"수정 후 소개","garden_color":"green"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/update-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `delete garden should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardendelete@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardendelete@example.com")?.userNo)

        val gardenNo = createGardenMembership(
            userNo = userNo,
            title = "삭제 대상 가든",
            info = "삭제 소개",
            color = "red",
            leader = true,
            main = false,
        )

        val response = mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/delete-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden move should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenmove@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmove@example.com")?.userNo)
        removeSignupGarden(userNo)

        val sourceGardenNo = createGardenMembership(
            userNo = userNo,
            title = "이동 전 가든",
            info = "출발",
            color = "yellow",
            leader = true,
            main = true,
        )
        val destinationGardenNo = createGardenMembership(
            userNo = userNo,
            title = "이동 후 가든",
            info = "도착",
            color = "purple",
            leader = false,
            main = false,
        )
        bookRepository.save(
            BookEntity(
                gardenNo = sourceGardenNo,
                bookTitle = "이동할 책",
                bookAuthor = "저자",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 210,
                bookIsbn = "9784444444444",
                bookTree = "소설",
                bookImageUrl = "https://example.com/move.jpg",
                bookInfo = "이동용 책",
            ),
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/to")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", sourceGardenNo.toString())
                .queryParam("to_garden_no", destinationGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/move-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `delete garden member leave should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenleave@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenleave@example.com")?.userNo)

        val leader = userRepository.save(
            UserEntity(
                userEmail = "garden-leave-leader@example.com",
                userPassword = "pw1234",
                userCreatedAt = LocalDateTime.of(2024, 3, 1, 9, 0, 0),
                userNick = "리더",
                userImage = "leader.png",
                userFcm = "leader-fcm",
                userSocialId = "",
                userSocialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "탈퇴 가든",
                gardenInfo = "탈퇴 소개",
                gardenColor = "mint",
                gardenCreatedAt = LocalDateTime.of(2024, 3, 1, 8, 30, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = checkNotNull(leader.userNo),
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 3, 1, 8, 40, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2024, 3, 1, 8, 50, 0),
            ),
        )

        val response = mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/leave-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden leader transfer should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenleader@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenleader@example.com")?.userNo)
        removeSignupGarden(userNo)

        val member = userRepository.save(
            UserEntity(
                userEmail = "garden-leader-member@example.com",
                userPassword = "pw1234",
                userCreatedAt = LocalDateTime.of(2024, 4, 1, 9, 30, 0),
                userNick = "새 리더",
                userImage = "member.png",
                userFcm = "member-fcm",
                userSocialId = "",
                userSocialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "리더 변경 가든",
                gardenInfo = "리더 소개",
                gardenColor = "navy",
                gardenCreatedAt = LocalDateTime.of(2024, 4, 1, 9, 0, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 4, 1, 9, 10, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = checkNotNull(member.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2024, 4, 1, 9, 20, 0),
            ),
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", checkNotNull(member.userNo).toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/leader-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden main should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenmain@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmain@example.com")?.userNo)

        val targetGardenNo = createGardenMembership(
            userNo = userNo,
            title = "메인 변경 대상",
            info = "메인 소개",
            color = "white",
            leader = true,
            main = false,
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/main")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/main-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `post garden invite should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardeninvite@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardeninvite@example.com")?.userNo)

        val leader = userRepository.save(
            UserEntity(
                userEmail = "garden-invite-leader@example.com",
                userPassword = "pw1234",
                userCreatedAt = LocalDateTime.of(2024, 5, 1, 9, 0, 0),
                userNick = "초대 리더",
                userImage = "leader.png",
                userFcm = "invite-leader-fcm",
                userSocialId = "",
                userSocialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "초대 가든",
                gardenInfo = "초대 소개",
                gardenColor = "pink",
                gardenCreatedAt = LocalDateTime.of(2024, 5, 1, 8, 0, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = checkNotNull(leader.userNo),
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 5, 1, 8, 10, 0),
            ),
        )

        val response = mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isCreated)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/invite-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get push should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("pushget@example.com")

        val response = mockMvc.perform(
            get("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/push/get-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put push should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("pushupdate@example.com")

        val response = mockMvc.perform(
            put("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_book_ok":true,"push_time":"2026-04-06T12:30:00"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/push/update-success.json"),
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

    private fun createGardenMembership(
        userNo: Int,
        title: String,
        info: String,
        color: String,
        leader: Boolean,
        main: Boolean,
    ): Int {
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = title,
                gardenInfo = info,
                gardenColor = color,
                gardenCreatedAt = LocalDateTime.of(2024, 1, 1, 9, 0, 0),
            ),
        )
        return checkNotNull(garden.gardenNo).also { gardenNo ->
            gardenUserRepository.save(
                GardenUserEntity(
                    gardenNo = gardenNo,
                    userNo = userNo,
                    gardenLeader = leader,
                    gardenMain = main,
                    gardenSignDate = LocalDateTime.of(2024, 1, 1, 9, 5, 0),
                ),
            )
        }
    }

    private fun removeSignupGarden(userNo: Int) {
        gardenUserRepository.findAllByUserNo(userNo).forEach { membership ->
            gardenUserRepository.delete(membership)
            gardenRepository.deleteById(membership.gardenNo)
        }
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
                    assertThat(actual.size())
                        .withFailMessage("$path should contain at least ${fixture.size()} elements")
                        .isGreaterThanOrEqualTo(fixture.size())
                    for (index in 0 until fixture.size()) {
                        assertSameShape(fixture[index], actual[index], "$path[$index]")
                    }
                }
            }

            fixture.isTextual -> {
                assertThat(actual.isTextual).withFailMessage("$path should be a string").isTrue()
                if (path in exactEnvelopeValuePaths) {
                    assertThat(actual.asText())
                        .withFailMessage("$path should match the fixture value")
                        .isEqualTo(fixture.asText())
                }
            }

            fixture.isNumber -> {
                assertThat(actual.isNumber).withFailMessage("$path should be a number").isTrue()
                if (path in exactEnvelopeValuePaths) {
                    assertThat(actual.decimalValue())
                        .withFailMessage("$path should match the fixture value")
                        .isEqualTo(fixture.decimalValue())
                }
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
