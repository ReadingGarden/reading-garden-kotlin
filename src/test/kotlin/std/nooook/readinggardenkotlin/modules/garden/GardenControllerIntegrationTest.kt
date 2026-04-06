package std.nooook.readinggardenkotlin.modules.garden

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.garden.service.GardenCommandService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.web.server.ResponseStatusException

@SpringBootTest
@AutoConfigureMockMvc
class GardenControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val gardenCommandService: GardenCommandService,
    @Autowired private val gardenService: GardenService,
) {
    companion object {
        private val imagesRoot: Path = Files.createTempDirectory("reading-garden-garden-images")

        @JvmStatic
        @DynamicPropertySource
        fun registerStorageProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.storage.images-root") { imagesRoot.toString() }
        }
    }

    @MockitoSpyBean
    private lateinit var pushService: PushService

    @MockitoSpyBean
    private lateinit var imageStorage: ImageStorage

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        cleanImagesRoot()
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
        reset(imageStorage)
        clearInvocations(pushService)
    }

    private fun cleanImagesRoot() {
        if (!Files.exists(imagesRoot)) {
            Files.createDirectories(imagesRoot)
            return
        }

        Files.walk(imagesRoot).use { paths ->
            paths
                .sorted(Comparator.reverseOrder())
                .filter { it != imagesRoot }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `create garden should persist garden and leader main membership`() {
        val accessToken = signupAndGetAccessToken("gardencreate@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardencreate@example.com")?.userNo)
        val previousMembershipCount = gardenUserRepository.countByUserNo(userNo)

        val response = mockMvc.perform(
            post("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"두 번째 가든","garden_info":"함께 읽기","garden_color":"yellow"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("가든 추가 성공"))
            .andExpect(jsonPath("$.data.garden_title").value("두 번째 가든"))
            .andReturn()

        val body = objectMapper.readTree(response.response.contentAsString)
        val gardenNo = body.path("data").path("garden_no").asInt()
        val savedGarden = checkNotNull(gardenRepository.findById(gardenNo).orElse(null))
        val memberships = gardenUserRepository.findAllByUserNo(userNo)
        val newMembership = memberships.single { it.gardenNo == gardenNo }

        assertEquals("두 번째 가든", savedGarden.gardenTitle)
        assertEquals("함께 읽기", savedGarden.gardenInfo)
        assertEquals("yellow", savedGarden.gardenColor)
        assertEquals(previousMembershipCount + 1, gardenUserRepository.countByUserNo(userNo))
        assertTrue(newMembership.gardenLeader)
        assertTrue(newMembership.gardenMain)
    }

    @Test
    fun `create garden should reject when user already has five gardens`() {
        val accessToken = signupAndGetAccessToken("gardenlimit@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenlimit@example.com")?.userNo)

        repeat(4) { index ->
            val garden = gardenRepository.save(
                GardenEntity(
                    gardenTitle = "추가 가든 ${index + 1}",
                    gardenInfo = "소개 ${index + 1}",
                    gardenColor = "green",
                ),
            )
            gardenUserRepository.save(
                GardenUserEntity(
                    gardenNo = checkNotNull(garden.gardenNo),
                    userNo = userNo,
                    gardenLeader = true,
                    gardenMain = true,
                ),
            )
        }

        val beforeGardenCount = gardenRepository.count()
        val beforeMembershipCount = gardenUserRepository.countByUserNo(userNo)

        mockMvc.perform(
            post("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"초과 가든","garden_info":"제한 테스트","garden_color":"red"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 생성 개수 초과"))

        assertEquals(beforeGardenCount, gardenRepository.count())
        assertEquals(beforeMembershipCount, gardenUserRepository.countByUserNo(userNo))
        assertFalse(gardenRepository.findAll().any { it.gardenTitle == "초과 가든" })
    }

    @Test
    fun `create garden should keep five garden limit under concurrent requests`() {
        signupAndGetAccessToken("gardenrace@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenrace@example.com")?.userNo)

        repeat(3) { index ->
            val garden = gardenRepository.save(
                GardenEntity(
                    gardenTitle = "기존 가든 ${index + 1}",
                    gardenInfo = "소개 ${index + 1}",
                    gardenColor = "green",
                ),
            )
            gardenUserRepository.save(
                GardenUserEntity(
                    gardenNo = checkNotNull(garden.gardenNo),
                    userNo = userNo,
                    gardenLeader = true,
                    gardenMain = true,
                ),
            )
        }

        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = listOf("동시 가든 A", "동시 가든 B").map { title ->
                executor.submit<String> {
                    startLatch.await(5, TimeUnit.SECONDS)
                    try {
                        gardenService.createGarden(
                            userNo = userNo,
                            request = CreateGardenRequest(
                                garden_title = title,
                                garden_info = "동시성 테스트",
                                garden_color = "purple",
                            ),
                        )
                        "success"
                    } catch (ex: ResponseStatusException) {
                        "status-${ex.statusCode.value()}"
                    }
                }
            }

            startLatch.countDown()
            val results = futures.map { future ->
                try {
                    future.get(5, TimeUnit.SECONDS)
                } catch (ex: ExecutionException) {
                    throw ex.cause ?: ex
                }
            }

            assertEquals(1, results.count { it == "success" })
            assertEquals(1, results.count { it == "status-403" })
            assertEquals(5L, gardenUserRepository.countByUserNo(userNo))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `garden list should return callers main garden first with legacy fields`() {
        val accessToken = signupAndGetAccessToken("gardenlist@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenlist@example.com")?.userNo)
        removeSignupGarden(userNo)

        val secondaryGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "서브 가든",
                gardenInfo = "두 번째",
                gardenColor = "blue",
                gardenCreatedAt = LocalDateTime.of(2024, 1, 10, 9, 0, 0),
            ),
        )
        val mainGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "메인 가든",
                gardenInfo = "첫 번째",
                gardenColor = "green",
                gardenCreatedAt = LocalDateTime.of(2024, 1, 5, 8, 30, 0),
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
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(mainGarden.gardenNo),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 1, 6, 7, 0, 0),
            ),
        )
        val teammate = userRepository.save(
            userRepository.findByUserNo(userNo)?.copyForGardenTest(
                userNo = null,
                userEmail = "gardenlist-teammate@example.com",
                userNick = "teammate",
            ) ?: error("User fixture missing"),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(mainGarden.gardenNo),
                userNo = checkNotNull(teammate.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2024, 1, 7, 7, 0, 0),
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

        mockMvc.perform(
            get("/api/v1/garden/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 리스트 조회 성공"))
            .andExpect(jsonPath("$.data[0].garden_no").value(checkNotNull(mainGarden.gardenNo)))
            .andExpect(jsonPath("$.data[0].garden_title").value("메인 가든"))
            .andExpect(jsonPath("$.data[0].garden_info").value("첫 번째"))
            .andExpect(jsonPath("$.data[0].garden_color").value("green"))
            .andExpect(jsonPath("$.data[0].garden_members").value(2))
            .andExpect(jsonPath("$.data[0].book_count").value(1))
            .andExpect(jsonPath("$.data[0].garden_created_at").value("2024-01-05T08:30:00"))
            .andExpect(jsonPath("$.data[1].garden_no").value(checkNotNull(secondaryGarden.gardenNo)))
            .andExpect(jsonPath("$.data[1].garden_members").value(1))
            .andExpect(jsonPath("$.data[1].book_count").value(1))
    }

    @Test
    fun `garden detail should return legacy shape with leader first and latest percent`() {
        val accessToken = signupAndGetAccessToken("gardendetail@example.com")
        val user = checkNotNull(userRepository.findByUserEmail("gardendetail@example.com"))
        val userNo = checkNotNull(user.userNo)
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
            userRepository.findByUserNo(userNo)?.copyForGardenTest(
                userNo = null,
                userEmail = "gardendetail-member@example.com",
                userNick = "member",
                userImage = "member.png",
            ) ?: error("User fixture missing"),
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
        val book = bookRepository.save(
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
        bookReadRepository.save(
            BookReadEntity(
                bookNo = checkNotNull(book.bookNo),
                bookCurrentPage = 120,
                createdAt = LocalDateTime.of(2024, 2, 2, 10, 0, 0),
                userNo = userNo,
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                bookNo = checkNotNull(book.bookNo),
                bookCurrentPage = 200,
                createdAt = LocalDateTime.of(2024, 2, 3, 10, 0, 0),
                userNo = userNo,
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.gardenNo).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 상세 조회 성공"))
            .andExpect(jsonPath("$.data.garden_no").value(checkNotNull(garden.gardenNo)))
            .andExpect(jsonPath("$.data.garden_title").value("상세 가든"))
            .andExpect(jsonPath("$.data.garden_info").value("상세 설명"))
            .andExpect(jsonPath("$.data.garden_color").value("orange"))
            .andExpect(jsonPath("$.data.garden_created_at").value("2024-02-01T12:00:00"))
            .andExpect(jsonPath("$.data.book_list[0].book_no").value(checkNotNull(book.bookNo)))
            .andExpect(jsonPath("$.data.book_list[0].book_isbn").value("9783333333333"))
            .andExpect(jsonPath("$.data.book_list[0].book_title").value("가든 책"))
            .andExpect(jsonPath("$.data.book_list[0].book_author").value("작가"))
            .andExpect(jsonPath("$.data.book_list[0].book_publisher").value("출판사"))
            .andExpect(jsonPath("$.data.book_list[0].book_info").value("가든 책 소개"))
            .andExpect(jsonPath("$.data.book_list[0].book_image_url").value("https://example.com/detail.jpg"))
            .andExpect(jsonPath("$.data.book_list[0].book_tree").value("인문"))
            .andExpect(jsonPath("$.data.book_list[0].book_status").value(1))
            .andExpect(jsonPath("$.data.book_list[0].percent").value(50.0))
            .andExpect(jsonPath("$.data.book_list[0].user_no").value(userNo))
            .andExpect(jsonPath("$.data.book_list[0].book_page").value(400))
            .andExpect(jsonPath("$.data.garden_members[0].user_no").value(userNo))
            .andExpect(jsonPath("$.data.garden_members[0].user_nick").value(user.userNick))
            .andExpect(jsonPath("$.data.garden_members[0].user_image").value(user.userImage))
            .andExpect(jsonPath("$.data.garden_members[0].garden_leader").value(true))
            .andExpect(jsonPath("$.data.garden_members[0].garden_sign_date").value("2024-02-01T12:05:00"))
            .andExpect(jsonPath("$.data.garden_members[1].user_no").value(checkNotNull(member.userNo)))
            .andExpect(jsonPath("$.data.garden_members[1].user_nick").value("member"))
            .andExpect(jsonPath("$.data.garden_members[1].user_image").value("member.png"))
            .andExpect(jsonPath("$.data.garden_members[1].garden_leader").value(false))
            .andExpect(jsonPath("$.data.garden_members[1].garden_sign_date").value("2024-02-02T09:00:00"))
    }

    @Test
    fun `garden detail should keep null legacy book fields and zero percent for non positive page`() {
        val accessToken = signupAndGetAccessToken("gardennulls@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardennulls@example.com")?.userNo)
        removeSignupGarden(userNo)
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "널 가든",
                gardenInfo = "널 필드 확인",
                gardenColor = "white",
                gardenCreatedAt = LocalDateTime.of(2024, 3, 1, 9, 0, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
                gardenSignDate = LocalDateTime.of(2024, 3, 1, 9, 5, 0),
            ),
        )
        val book = bookRepository.save(
            BookEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                bookTitle = "페이지 0 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 0,
                userNo = userNo,
                bookPage = 0,
                bookIsbn = null,
                bookTree = null,
                bookImageUrl = null,
                bookInfo = "널과 0 확인",
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                bookNo = checkNotNull(book.bookNo),
                bookCurrentPage = 10,
                createdAt = LocalDateTime.of(2024, 3, 2, 9, 0, 0),
                userNo = userNo,
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.gardenNo).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.book_list[0].book_no").value(checkNotNull(book.bookNo)))
            .andExpect(jsonPath("$.data.book_list[0].book_isbn").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].book_image_url").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].book_tree").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].percent").value(0.0))
            .andExpect(jsonPath("$.data.book_list[0].book_page").value(0))
    }

    @Test
    fun `garden detail should reject when caller is not a member`() {
        val ownerToken = signupAndGetAccessToken("gardenowner@example.com")
        val ownerNo = checkNotNull(userRepository.findByUserEmail("gardenowner@example.com")?.userNo)
        removeSignupGarden(ownerNo)
        val outsiderToken = signupAndGetAccessToken("gardenoutsider@example.com")
        val outsiderNo = checkNotNull(userRepository.findByUserEmail("gardenoutsider@example.com")?.userNo)
        removeSignupGarden(outsiderNo)
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "비공개 가든",
                gardenInfo = "소속 제한",
                gardenColor = "black",
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = checkNotNull(garden.gardenNo),
                userNo = ownerNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .queryParam("garden_no", checkNotNull(garden.gardenNo).toString()),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $outsiderToken")
                .queryParam("garden_no", checkNotNull(garden.gardenNo).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }

    @Test
    fun `update garden should require caller to be leader`() {
        val ownerToken = signupAndGetAccessToken("gardenupdateowner@example.com")
        val ownerNo = checkNotNull(userRepository.findByUserEmail("gardenupdateowner@example.com")?.userNo)
        removeSignupGarden(ownerNo)
        val memberToken = signupAndGetAccessToken("gardenupdatemember@example.com")
        val memberNo = checkNotNull(userRepository.findByUserEmail("gardenupdatemember@example.com")?.userNo)
        removeSignupGarden(memberNo)

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "수정 전 가든",
                gardenInfo = "수정 전 소개",
                gardenColor = "green",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = ownerNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = memberNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                .queryParam("garden_no", gardenNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"수정 시도","garden_info":"권한 없음","garden_color":"red"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 수정 불가"))

        val unchangedGarden = checkNotNull(gardenRepository.findById(gardenNo).orElse(null))
        assertEquals("수정 전 가든", unchangedGarden.gardenTitle)
        assertEquals("수정 전 소개", unchangedGarden.gardenInfo)
        assertEquals("green", unchangedGarden.gardenColor)

        mockMvc.perform(
            put("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .queryParam("garden_no", gardenNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"리더 수정","garden_info":"리더만 가능","garden_color":"red"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 수정 성공"))
            .andExpect(jsonPath("$.data").isMap)
            .andExpect(jsonPath("$.data").isEmpty)

        val updatedGarden = checkNotNull(gardenRepository.findById(gardenNo).orElse(null))
        assertEquals("리더 수정", updatedGarden.gardenTitle)
        assertEquals("리더만 가능", updatedGarden.gardenInfo)
        assertEquals("red", updatedGarden.gardenColor)
    }

    @Test
    fun `delete garden should forbid shared garden even for leader`() {
        val leaderToken = signupAndGetAccessToken("gardendeletesharedleader@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardendeletesharedleader@example.com")?.userNo)
        removeSignupGarden(leaderNo)
        val teammateToken = signupAndGetAccessToken("gardendeletesharedmate@example.com")
        val teammateNo = checkNotNull(userRepository.findByUserEmail("gardendeletesharedmate@example.com")?.userNo)
        removeSignupGarden(teammateNo)

        val sharedGardenNo = createGardenMembership(
            userNo = leaderNo,
            title = "공유 가든",
            isLeader = true,
            isMain = false,
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = sharedGardenNo,
                userNo = teammateNo,
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2026, 4, 6, 10, 5, 0),
            ),
        )
        createGardenMembership(
            userNo = leaderNo,
            title = "남아야 할 가든",
            isLeader = true,
            isMain = true,
        )
        createGardenMembership(
            userNo = teammateNo,
            title = "팀원 다른 가든",
            isLeader = true,
            isMain = true,
        )

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", sharedGardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 불가"))

        assertTrue(gardenRepository.existsById(sharedGardenNo))
        assertEquals(2L, gardenUserRepository.countByGardenNo(sharedGardenNo))
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(sharedGardenNo, leaderNo)?.gardenLeader == true)

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $teammateToken")
                .queryParam("garden_no", sharedGardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 불가"))
    }

    @Test
    fun `delete garden should forbid non leader when leader check is deciding condition`() {
        val memberToken = signupAndGetAccessToken("gardendeletenonleader@example.com")
        val memberNo = checkNotNull(userRepository.findByUserEmail("gardendeletenonleader@example.com")?.userNo)
        removeSignupGarden(memberNo)

        createGardenMembership(
            userNo = memberNo,
            title = "남아야 할 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userNo = memberNo,
            title = "비리더 단독 가든",
            isLeader = false,
            isMain = false,
        )

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 불가"))

        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, memberNo)?.gardenLeader == false)
        assertEquals(1L, gardenUserRepository.countByGardenNo(targetGardenNo))
        assertEquals(2L, gardenUserRepository.countByUserNo(memberNo))
    }

    @Test
    fun `delete garden should delete owned resources and image artifacts`() {
        val accessToken = signupAndGetAccessToken("gardendeletesolo@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardendeletesolo@example.com")?.userNo)
        removeSignupGarden(userNo)

        createGardenMembership(
            userNo = userNo,
            title = "유지 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userNo = userNo,
            title = "삭제 대상 가든",
            isLeader = true,
            isMain = false,
        )

        val ownedBook = bookRepository.save(
            BookEntity(
                gardenNo = targetGardenNo,
                bookTitle = "삭제 대상 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 300,
                bookIsbn = "9787600000000",
                bookTree = "seed",
                bookInfo = "삭제될 책",
            ),
        )
        val ownedBookNo = checkNotNull(ownedBook.bookNo)
        bookReadRepository.save(
            BookReadEntity(
                bookNo = ownedBookNo,
                bookCurrentPage = 123,
                userNo = userNo,
            ),
        )

        val bookImagePath = "garden/delete/book-image.png"
        createStoredImage(bookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                bookNo = ownedBookNo,
                imageName = "book-image.png",
                imageUrl = bookImagePath,
            ),
        )

        val memo = memoRepository.save(
            MemoEntity(
                bookNo = ownedBookNo,
                memoContent = "삭제될 메모",
                userNo = userNo,
                memoLike = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        val memoImagePath = "garden/delete/memo-image.png"
        createStoredImage(memoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memoNo = memoNo,
                imageName = "memo-image.png",
                imageUrl = memoImagePath,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 성공"))

        assertFalse(gardenRepository.existsById(targetGardenNo))
        assertEquals(null, gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, userNo))
        assertFalse(bookRepository.findById(ownedBookNo).isPresent)
        assertTrue(bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(ownedBookNo).isEmpty())
        assertTrue(bookImageRepository.findAllByBookNo(ownedBookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookNo(ownedBookNo).isEmpty())
        assertTrue(memoImageRepository.findAllByMemoNoIn(listOf(memoNo)).isEmpty())
        assertFalse(Files.exists(imagesRoot.resolve(bookImagePath)))
        assertFalse(Files.exists(imagesRoot.resolve(memoImagePath)))
    }

    @Test
    fun `delete garden should roll back staged image cleanup when later stage delete fails`() {
        val accessToken = signupAndGetAccessToken("gardendeleterollback@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardendeleterollback@example.com")?.userNo)
        removeSignupGarden(userNo)

        createGardenMembership(
            userNo = userNo,
            title = "유지 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userNo = userNo,
            title = "롤백 대상 가든",
            isLeader = true,
            isMain = false,
        )

        val ownedBook = bookRepository.save(
            BookEntity(
                gardenNo = targetGardenNo,
                bookTitle = "롤백 대상 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 280,
                bookIsbn = "9787600000009",
                bookTree = "seed",
                bookInfo = "롤백 확인용 책",
            ),
        )
        val ownedBookNo = checkNotNull(ownedBook.bookNo)
        bookReadRepository.save(
            BookReadEntity(
                bookNo = ownedBookNo,
                bookCurrentPage = 111,
                userNo = userNo,
            ),
        )

        val bookImagePath = "garden/rollback/book-image.png"
        createStoredImage(bookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                bookNo = ownedBookNo,
                imageName = "book-image.png",
                imageUrl = bookImagePath,
            ),
        )

        val memo = memoRepository.save(
            MemoEntity(
                bookNo = ownedBookNo,
                memoContent = "롤백 대상 메모",
                userNo = userNo,
                memoLike = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        val memoImagePath = "garden/rollback/memo-image.png"
        createStoredImage(memoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memoNo = memoNo,
                imageName = "memo-image.png",
                imageUrl = memoImagePath,
            ),
        )

        var stageDeleteCount = 0
        doAnswer { invocation ->
            stageDeleteCount += 1
            if (stageDeleteCount == 2) {
                throw IllegalStateException("Injected staged delete failure")
            }
            invocation.callRealMethod()
        }.`when`(imageStorage).stageDelete(anyString())

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.resp_code").value(500))
            .andExpect(jsonPath("$.resp_msg").value("An unexpected error occurred."))

        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, userNo) != null)
        assertTrue(bookRepository.findById(ownedBookNo).isPresent)
        assertEquals(1, bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(ownedBookNo).size)
        assertEquals(1, bookImageRepository.findAllByBookNo(ownedBookNo).size)
        assertEquals(1, memoRepository.findAllByBookNo(ownedBookNo).size)
        assertEquals(1, memoImageRepository.findAllByMemoNoIn(listOf(memoNo)).size)
        assertTrue(Files.exists(imagesRoot.resolve(bookImagePath)))
        assertTrue(Files.exists(imagesRoot.resolve(memoImagePath)))
    }

    @Test
    fun `delete garden should forbid when caller would lose last membership`() {
        val accessToken = signupAndGetAccessToken("gardendeletelast@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardendeletelast@example.com")?.userNo)
        val targetMembership = gardenUserRepository.findAllByUserNo(userNo).single()

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetMembership.gardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 불가"))

        assertTrue(gardenRepository.existsById(targetMembership.gardenNo))
        assertEquals(1L, gardenUserRepository.countByUserNo(userNo))
    }

    @Test
    fun `leave garden member should delete owned resources and transfer leader to earliest member`() {
        val leaderToken = signupAndGetAccessToken("gardenleaveleader@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardenleaveleader@example.com")?.userNo)
        removeSignupGarden(leaderNo)

        val earliestMember = userRepository.save(
            checkNotNull(userRepository.findByUserNo(leaderNo)).copyForGardenTest(
                userNo = null,
                userEmail = "gardenleaveearliest@example.com",
                userNick = "earliest-member",
            ),
        )
        val laterMember = userRepository.save(
            checkNotNull(userRepository.findByUserNo(leaderNo)).copyForGardenTest(
                userNo = null,
                userEmail = "gardenleavelater@example.com",
                userNick = "later-member",
            ),
        )

        createGardenMembership(
            userNo = leaderNo,
            title = "남는 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "탈퇴 대상 가든",
                gardenInfo = "리더 위임",
                gardenColor = "green",
            ),
        )
        val targetGardenNo = checkNotNull(targetGarden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = targetGardenNo,
                userNo = leaderNo,
                gardenLeader = true,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2026, 4, 6, 9, 0, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = targetGardenNo,
                userNo = checkNotNull(earliestMember.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2026, 4, 6, 9, 1, 0),
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = targetGardenNo,
                userNo = checkNotNull(laterMember.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2026, 4, 6, 9, 2, 0),
            ),
        )

        val leaderBook = bookRepository.save(
            BookEntity(
                gardenNo = targetGardenNo,
                bookTitle = "리더 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = leaderNo,
                bookPage = 320,
                bookIsbn = "9787610000000",
                bookTree = "sprout",
                bookInfo = "리더 책 설명",
            ),
        )
        val leaderBookNo = checkNotNull(leaderBook.bookNo)
        bookReadRepository.save(
            BookReadEntity(
                bookNo = leaderBookNo,
                bookCurrentPage = 200,
                userNo = leaderNo,
            ),
        )

        val leaderBookImagePath = "garden/leave/leader-book.png"
        createStoredImage(leaderBookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                bookNo = leaderBookNo,
                imageName = "leader-book.png",
                imageUrl = leaderBookImagePath,
            ),
        )

        val leaderMemo = memoRepository.save(
            MemoEntity(
                bookNo = leaderBookNo,
                memoContent = "리더 메모",
                userNo = leaderNo,
                memoLike = true,
            ),
        )
        val leaderMemoNo = checkNotNull(leaderMemo.id)

        val leaderMemoImagePath = "garden/leave/leader-memo.png"
        createStoredImage(leaderMemoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memoNo = leaderMemoNo,
                imageName = "leader-memo.png",
                imageUrl = leaderMemoImagePath,
            ),
        )

        val teammateBook = bookRepository.save(
            BookEntity(
                gardenNo = targetGardenNo,
                bookTitle = "남는 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = checkNotNull(earliestMember.userNo),
                bookPage = 210,
                bookIsbn = "9787610000001",
                bookTree = "leaf",
                bookInfo = "팀원 책 설명",
            ),
        )
        val teammateBookNo = checkNotNull(teammateBook.bookNo)

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 성공"))

        assertEquals(null, gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, leaderNo))
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, checkNotNull(earliestMember.userNo))?.gardenLeader == true)
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, checkNotNull(laterMember.userNo))?.gardenLeader == false)
        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertFalse(bookRepository.findById(leaderBookNo).isPresent)
        assertTrue(bookRepository.findById(teammateBookNo).isPresent)
        assertTrue(bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(leaderBookNo).isEmpty())
        assertTrue(bookImageRepository.findAllByBookNo(leaderBookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookNo(leaderBookNo).isEmpty())
        assertTrue(memoImageRepository.findAllByMemoNoIn(listOf(leaderMemoNo)).isEmpty())
        assertFalse(Files.exists(imagesRoot.resolve(leaderBookImagePath)))
        assertFalse(Files.exists(imagesRoot.resolve(leaderMemoImagePath)))
    }

    @Test
    fun `leave garden member should roll back staged image cleanup when later stage delete fails`() {
        val leaderToken = signupAndGetAccessToken("gardenleaverollback@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardenleaverollback@example.com")?.userNo)
        removeSignupGarden(leaderNo)

        val otherMember = userRepository.save(
            checkNotNull(userRepository.findByUserNo(leaderNo)).copyForGardenTest(
                userNo = null,
                userEmail = "gardenleaverollback-member@example.com",
                userNick = "rollback-member",
            ),
        )

        createGardenMembership(
            userNo = leaderNo,
            title = "남는 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userNo = leaderNo,
            title = "탈퇴 롤백 가든",
            isLeader = true,
            isMain = false,
            signDate = LocalDateTime.of(2026, 4, 6, 8, 59, 0),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = targetGardenNo,
                userNo = checkNotNull(otherMember.userNo),
                gardenLeader = false,
                gardenMain = false,
                gardenSignDate = LocalDateTime.of(2026, 4, 6, 9, 1, 0),
            ),
        )

        val leaderBook = bookRepository.save(
            BookEntity(
                gardenNo = targetGardenNo,
                bookTitle = "탈퇴 롤백 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = leaderNo,
                bookPage = 320,
                bookIsbn = "9787610000010",
                bookTree = "sprout",
                bookInfo = "탈퇴 롤백 확인용 책",
            ),
        )
        val leaderBookNo = checkNotNull(leaderBook.bookNo)
        bookReadRepository.save(
            BookReadEntity(
                bookNo = leaderBookNo,
                bookCurrentPage = 144,
                userNo = leaderNo,
            ),
        )

        val leaderBookImagePath = "garden/leave-rollback/leader-book.png"
        createStoredImage(leaderBookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                bookNo = leaderBookNo,
                imageName = "leader-book.png",
                imageUrl = leaderBookImagePath,
            ),
        )

        val leaderMemo = memoRepository.save(
            MemoEntity(
                bookNo = leaderBookNo,
                memoContent = "탈퇴 롤백 메모",
                userNo = leaderNo,
                memoLike = false,
            ),
        )
        val leaderMemoNo = checkNotNull(leaderMemo.id)

        val leaderMemoImagePath = "garden/leave-rollback/leader-memo.png"
        createStoredImage(leaderMemoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memoNo = leaderMemoNo,
                imageName = "leader-memo.png",
                imageUrl = leaderMemoImagePath,
            ),
        )

        var stageDeleteCount = 0
        doAnswer { invocation ->
            stageDeleteCount += 1
            if (stageDeleteCount == 2) {
                throw IllegalStateException("Injected staged delete failure for leave")
            }
            invocation.callRealMethod()
        }.`when`(imageStorage).stageDelete(anyString())

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.resp_code").value(500))
            .andExpect(jsonPath("$.resp_msg").value("An unexpected error occurred."))

        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, leaderNo)?.gardenLeader == true)
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, checkNotNull(otherMember.userNo))?.gardenLeader == false)
        assertTrue(bookRepository.findById(leaderBookNo).isPresent)
        assertEquals(1, bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(leaderBookNo).size)
        assertEquals(1, bookImageRepository.findAllByBookNo(leaderBookNo).size)
        assertEquals(1, memoRepository.findAllByBookNo(leaderBookNo).size)
        assertEquals(1, memoImageRepository.findAllByMemoNoIn(listOf(leaderMemoNo)).size)
        assertTrue(Files.exists(imagesRoot.resolve(leaderBookImagePath)))
        assertTrue(Files.exists(imagesRoot.resolve(leaderMemoImagePath)))
    }

    @Test
    fun `leave garden member should forbid when garden has one member`() {
        val accessToken = signupAndGetAccessToken("gardenleavealone@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenleavealone@example.com")?.userNo)
        val targetMembership = gardenUserRepository.findAllByUserNo(userNo).single()

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetMembership.gardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 불가"))

        assertTrue(gardenRepository.existsById(targetMembership.gardenNo))
        assertEquals(1L, gardenUserRepository.countByGardenNo(targetMembership.gardenNo))
    }

    @Test
    fun `leave garden member should forbid non member`() {
        val memberToken = signupAndGetAccessToken("gardenleavemember@example.com")
        val memberNo = checkNotNull(userRepository.findByUserEmail("gardenleavemember@example.com")?.userNo)
        removeSignupGarden(memberNo)
        val outsiderToken = signupAndGetAccessToken("gardenleaveoutsider@example.com")
        val outsiderNo = checkNotNull(userRepository.findByUserEmail("gardenleaveoutsider@example.com")?.userNo)
        removeSignupGarden(outsiderNo)

        val targetGardenNo = createGardenMembership(
            userNo = memberNo,
            title = "멤버 가든",
            isLeader = true,
            isMain = true,
        )
        createGardenMembership(
            userNo = outsiderNo,
            title = "외부인 가든",
            isLeader = true,
            isMain = true,
        )

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $outsiderToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 불가"))

        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertEquals(1L, gardenUserRepository.countByGardenNo(targetGardenNo))
        assertEquals(null, gardenUserRepository.findByGardenNoAndUserNo(targetGardenNo, outsiderNo))
    }

    @Test
    fun `move garden should reject when destination would exceed thirty books`() {
        val accessToken = signupAndGetAccessToken("gardenmovelimit@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmovelimit@example.com")?.userNo)
        removeSignupGarden(userNo)

        val sourceGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "출발 가든",
                gardenInfo = "옮기기 전",
                gardenColor = "blue",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "도착 가든",
                gardenInfo = "옮기기 후",
                gardenColor = "yellow",
            ),
        )
        val sourceGardenNo = checkNotNull(sourceGarden.gardenNo)
        val destinationGardenNo = checkNotNull(destinationGarden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = sourceGardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = destinationGardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = false,
            ),
        )

        repeat(2) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = sourceGardenNo,
                    bookTitle = "이동 대상 ${index + 1}",
                    bookAuthor = "작가",
                    bookPublisher = "출판사",
                    bookStatus = 1,
                    userNo = userNo,
                    bookPage = 100,
                    bookIsbn = "978700000000$index",
                    bookTree = "소설",
                    bookImageUrl = null,
                    bookInfo = "출발 가든 책",
                ),
            )
        }
        repeat(29) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = destinationGardenNo,
                    bookTitle = "도착 책 ${index + 1}",
                    bookAuthor = "작가",
                    bookPublisher = "출판사",
                    bookStatus = 1,
                    userNo = userNo,
                    bookPage = 100,
                    bookIsbn = "9787100000${index.toString().padStart(3, '0')}",
                    bookTree = "소설",
                    bookImageUrl = null,
                    bookInfo = "도착 가든 책",
                ),
            )
        }

        mockMvc.perform(
            put("/api/v1/garden/to")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", sourceGardenNo.toString())
                .queryParam("to_garden_no", destinationGardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 옮기기 불가"))

        assertEquals(2, bookRepository.findAllByUserNoAndGardenNo(userNo, sourceGardenNo).size)
        assertEquals(29, bookRepository.findAllByGardenNoOrderByBookNoAsc(destinationGardenNo).size)
    }

    @Test
    fun `move garden should move only caller owned books`() {
        val accessToken = signupAndGetAccessToken("gardenmoveowned@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmoveowned@example.com")?.userNo)
        removeSignupGarden(userNo)
        val teammate = userRepository.save(
            userRepository.findByUserNo(userNo)?.copyForGardenTest(
                userNo = null,
                userEmail = "gardenmoveownedteammate@example.com",
                userNick = "teammate-move",
            ) ?: error("User fixture missing"),
        )

        val sourceGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "출발 가든",
                gardenInfo = "내 책만 이동",
                gardenColor = "purple",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "도착 가든",
                gardenInfo = "도착",
                gardenColor = "orange",
            ),
        )
        val sourceGardenNo = checkNotNull(sourceGarden.gardenNo)
        val destinationGardenNo = checkNotNull(destinationGarden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = sourceGardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = destinationGardenNo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = sourceGardenNo,
                userNo = checkNotNull(teammate.userNo),
                gardenLeader = false,
                gardenMain = false,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = destinationGardenNo,
                userNo = checkNotNull(teammate.userNo),
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        val callerBook = bookRepository.save(
            BookEntity(
                gardenNo = sourceGardenNo,
                bookTitle = "내 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = userNo,
                bookPage = 100,
                bookIsbn = "9787200000000",
                bookTree = "소설",
                bookImageUrl = null,
                bookInfo = "내 책 설명",
            ),
        )
        val teammateBook = bookRepository.save(
            BookEntity(
                gardenNo = sourceGardenNo,
                bookTitle = "팀원 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                userNo = checkNotNull(teammate.userNo),
                bookPage = 100,
                bookIsbn = "9787200000001",
                bookTree = "소설",
                bookImageUrl = null,
                bookInfo = "팀원 책 설명",
            ),
        )

        mockMvc.perform(
            put("/api/v1/garden/to")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", sourceGardenNo.toString())
                .queryParam("to_garden_no", destinationGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 책 이동 성공"))

        assertEquals(destinationGardenNo, bookRepository.findById(checkNotNull(callerBook.bookNo)).orElseThrow().gardenNo)
        assertEquals(sourceGardenNo, bookRepository.findById(checkNotNull(teammateBook.bookNo)).orElseThrow().gardenNo)
    }

    @Test
    fun `move garden should keep thirty book limit under concurrent moves to same destination`() {
        signupAndGetAccessToken("gardenmovelock@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmovelock@example.com")?.userNo)
        removeSignupGarden(userNo)

        val sourceGardenA = gardenRepository.save(
            GardenEntity(
                gardenTitle = "출발 가든 A",
                gardenInfo = "동시 이동 A",
                gardenColor = "blue",
            ),
        )
        val sourceGardenB = gardenRepository.save(
            GardenEntity(
                gardenTitle = "출발 가든 B",
                gardenInfo = "동시 이동 B",
                gardenColor = "purple",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "도착 가든",
                gardenInfo = "동시 도착지",
                gardenColor = "yellow",
            ),
        )
        val sourceGardenANo = checkNotNull(sourceGardenA.gardenNo)
        val sourceGardenBNo = checkNotNull(sourceGardenB.gardenNo)
        val destinationGardenNo = checkNotNull(destinationGarden.gardenNo)

        listOf(sourceGardenANo, sourceGardenBNo, destinationGardenNo).forEachIndexed { index, gardenNo ->
            gardenUserRepository.save(
                GardenUserEntity(
                    gardenNo = gardenNo,
                    userNo = userNo,
                    gardenLeader = index == 0,
                    gardenMain = index == 0,
                ),
            )
        }

        repeat(15) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = sourceGardenANo,
                    bookTitle = "A 책 ${index + 1}",
                    bookAuthor = "작가",
                    bookPublisher = "출판사",
                    bookStatus = 1,
                    userNo = userNo,
                    bookPage = 100,
                    bookIsbn = "9787300000${index.toString().padStart(3, '0')}",
                    bookTree = "소설",
                    bookInfo = "A 출발 책",
                ),
            )
        }
        repeat(15) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = sourceGardenBNo,
                    bookTitle = "B 책 ${index + 1}",
                    bookAuthor = "작가",
                    bookPublisher = "출판사",
                    bookStatus = 1,
                    userNo = userNo,
                    bookPage = 100,
                    bookIsbn = "9787310000${index.toString().padStart(3, '0')}",
                    bookTree = "소설",
                    bookInfo = "B 출발 책",
                ),
            )
        }
        repeat(10) { index ->
            bookRepository.save(
                BookEntity(
                    gardenNo = destinationGardenNo,
                    bookTitle = "도착 책 ${index + 1}",
                    bookAuthor = "작가",
                    bookPublisher = "출판사",
                    bookStatus = 1,
                    userNo = userNo,
                    bookPage = 100,
                    bookIsbn = "9787320000${index.toString().padStart(3, '0')}",
                    bookTree = "소설",
                    bookInfo = "도착 책",
                ),
            )
        }

        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = listOf(sourceGardenANo, sourceGardenBNo).map { sourceGardenNo ->
                executor.submit<String> {
                    startLatch.await(5, TimeUnit.SECONDS)
                    try {
                        gardenCommandService.moveGardenBook(userNo, sourceGardenNo, destinationGardenNo)
                        "success"
                    } catch (ex: ResponseStatusException) {
                        "status-${ex.statusCode.value()}"
                    }
                }
            }

            startLatch.countDown()
            val results = futures.map { future ->
                try {
                    future.get(5, TimeUnit.SECONDS)
                } catch (ex: ExecutionException) {
                    throw ex.cause ?: ex
                }
            }

            assertEquals(1, results.count { it == "success" })
            assertEquals(1, results.count { it == "status-403" })
            assertEquals(25, bookRepository.findAllByGardenNoOrderByBookNoAsc(destinationGardenNo).size)
            assertTrue(
                setOf(
                    bookRepository.findAllByUserNoAndGardenNo(userNo, sourceGardenANo).size,
                    bookRepository.findAllByUserNoAndGardenNo(userNo, sourceGardenBNo).size,
                ).contains(15),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `update garden main should keep only one main membership`() {
        val accessToken = signupAndGetAccessToken("gardenmainswitch@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmainswitch@example.com")?.userNo)
        removeSignupGarden(userNo)

        val mainGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "기존 메인",
                gardenInfo = "원래 메인",
                gardenColor = "green",
            ),
        )
        val targetGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "새 메인",
                gardenInfo = "바꿀 메인",
                gardenColor = "red",
            ),
        )
        val mainGardenNo = checkNotNull(mainGarden.gardenNo)
        val targetGardenNo = checkNotNull(targetGarden.gardenNo)
        val currentMainMembership = gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = mainGardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        val targetMembership = gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = targetGardenNo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/garden/main")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 메인 변경 성공"))

        val refreshedCurrentMain = gardenUserRepository.findById(checkNotNull(currentMainMembership.id)).orElseThrow()
        val refreshedTargetMembership = gardenUserRepository.findById(checkNotNull(targetMembership.id)).orElseThrow()
        val mainMemberships = gardenUserRepository.findAllByUserNo(userNo).filter { it.gardenMain }

        assertFalse(refreshedCurrentMain.gardenMain)
        assertTrue(refreshedTargetMembership.gardenMain)
        assertEquals(1, mainMemberships.size)
        assertEquals(targetGardenNo, mainMemberships.single().gardenNo)
    }

    @Test
    fun `update garden main should keep one main membership under concurrent switches`() {
        signupAndGetAccessToken("gardenmainlock@example.com")
        val userNo = checkNotNull(userRepository.findByUserEmail("gardenmainlock@example.com")?.userNo)
        removeSignupGarden(userNo)

        val originalMainGarden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "원래 메인",
                gardenInfo = "초기 메인",
                gardenColor = "green",
            ),
        )
        val candidateGardenA = gardenRepository.save(
            GardenEntity(
                gardenTitle = "후보 A",
                gardenInfo = "첫 번째 후보",
                gardenColor = "blue",
            ),
        )
        val candidateGardenB = gardenRepository.save(
            GardenEntity(
                gardenTitle = "후보 B",
                gardenInfo = "두 번째 후보",
                gardenColor = "red",
            ),
        )
        val originalMainGardenNo = checkNotNull(originalMainGarden.gardenNo)
        val candidateGardenANo = checkNotNull(candidateGardenA.gardenNo)
        val candidateGardenBNo = checkNotNull(candidateGardenB.gardenNo)

        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = originalMainGardenNo,
                userNo = userNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = candidateGardenANo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = candidateGardenBNo,
                userNo = userNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = listOf(candidateGardenANo, candidateGardenBNo).map { targetGardenNo ->
                executor.submit<String> {
                    startLatch.await(5, TimeUnit.SECONDS)
                    gardenCommandService.updateGardenMain(userNo, targetGardenNo)
                }
            }

            startLatch.countDown()
            futures.forEach { future ->
                try {
                    future.get(5, TimeUnit.SECONDS)
                } catch (ex: ExecutionException) {
                    throw ex.cause ?: ex
                }
            }

            val mainMemberships = gardenUserRepository.findAllByUserNo(userNo).filter { it.gardenMain }
            assertEquals(1, mainMemberships.size)
            assertTrue(mainMemberships.single().gardenNo == candidateGardenANo || mainMemberships.single().gardenNo == candidateGardenBNo)
            assertFalse(gardenUserRepository.findByGardenNoAndUserNo(originalMainGardenNo, userNo)?.gardenMain ?: true)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `update garden member should transfer leader only to same garden member`() {
        val leaderToken = signupAndGetAccessToken("gardenleadertransfer@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardenleadertransfer@example.com")?.userNo)
        removeSignupGarden(leaderNo)
        val targetToken = signupAndGetAccessToken("gardentargettransfer@example.com")
        val targetNo = checkNotNull(userRepository.findByUserEmail("gardentargettransfer@example.com")?.userNo)
        removeSignupGarden(targetNo)
        val outsiderToken = signupAndGetAccessToken("gardenoutsidertransfer@example.com")
        val outsiderNo = checkNotNull(userRepository.findByUserEmail("gardenoutsidertransfer@example.com")?.userNo)
        removeSignupGarden(outsiderNo)

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "리더 위임 가든",
                gardenInfo = "대표 이전 확인",
                gardenColor = "green",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = leaderNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = targetNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $outsiderToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", targetNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))

        mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", outsiderNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))

        mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", targetNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 멤버 변경 성공"))

        assertFalse(gardenUserRepository.findByGardenNoAndUserNo(gardenNo, leaderNo)?.gardenLeader ?: true)
        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(gardenNo, targetNo)?.gardenLeader ?: false)

        mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $targetToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", leaderNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 멤버 변경 성공"))

        assertTrue(gardenUserRepository.findByGardenNoAndUserNo(gardenNo, leaderNo)?.gardenLeader ?: false)
        assertFalse(gardenUserRepository.findByGardenNoAndUserNo(gardenNo, targetNo)?.gardenLeader ?: true)
    }

    @Test
    fun `invite garden should join caller as non leader non main and notify existing members`() {
        val leaderToken = signupAndGetAccessToken("gardeninviteleader@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardeninviteleader@example.com")?.userNo)
        removeSignupGarden(leaderNo)
        val existingMemberToken = signupAndGetAccessToken("gardeninviteexisting@example.com")
        val existingMemberNo = checkNotNull(userRepository.findByUserEmail("gardeninviteexisting@example.com")?.userNo)
        removeSignupGarden(existingMemberNo)
        val joinerToken = signupAndGetAccessToken("gardeninvitejoiner@example.com")
        val joinerNo = checkNotNull(userRepository.findByUserEmail("gardeninvitejoiner@example.com")?.userNo)
        removeSignupGarden(joinerNo)

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "초대 가든",
                gardenInfo = "셀프 조인",
                gardenColor = "blue",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = leaderNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = existingMemberNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $joinerToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("가든 초대 완료"))

        val joinedMembership = checkNotNull(gardenUserRepository.findByGardenNoAndUserNo(gardenNo, joinerNo))
        assertFalse(joinedMembership.gardenLeader)
        assertFalse(joinedMembership.gardenMain)
        assertEquals(3L, gardenUserRepository.countByGardenNo(gardenNo))
        verify(pushService).sendNewMemberPush(leaderNo, gardenNo)
        verify(pushService).sendNewMemberPush(existingMemberNo, gardenNo)
        verifyNoMoreInteractions(pushService)
    }

    @Test
    fun `invite garden should reject duplicate membership`() {
        val leaderToken = signupAndGetAccessToken("gardeninviteleaderduplicate@example.com")
        val leaderNo = checkNotNull(userRepository.findByUserEmail("gardeninviteleaderduplicate@example.com")?.userNo)
        removeSignupGarden(leaderNo)
        val memberToken = signupAndGetAccessToken("gardeninvitememberduplicate@example.com")
        val memberNo = checkNotNull(userRepository.findByUserEmail("gardeninvitememberduplicate@example.com")?.userNo)
        removeSignupGarden(memberNo)

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "중복 가든",
                gardenInfo = "중복 가입 방지",
                gardenColor = "yellow",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = leaderNo,
                gardenLeader = true,
                gardenMain = true,
            ),
        )
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = memberNo,
                gardenLeader = false,
                gardenMain = false,
            ),
        )

        mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resp_code").value(409))
            .andExpect(jsonPath("$.resp_msg").value("이미 가입된 가든"))

        assertEquals(2L, gardenUserRepository.countByGardenNo(gardenNo))
        verifyNoInteractions(pushService)
    }

    @Test
    fun `invite garden should reject when garden member capacity exceeds ten`() {
        val joinerToken = signupAndGetAccessToken("gardeninvitecapacityjoiner@example.com")
        val joinerNo = checkNotNull(userRepository.findByUserEmail("gardeninvitecapacityjoiner@example.com")?.userNo)
        removeSignupGarden(joinerNo)
        val seedUser = checkNotNull(userRepository.findByUserNo(joinerNo))

        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = "정원 초과 가든",
                gardenInfo = "멤버 제한 확인",
                gardenColor = "purple",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)

        repeat(10) { index ->
            val memberNo = checkNotNull(
                userRepository.save(
                    seedUser.copyForGardenTest(
                        userNo = null,
                        userEmail = if (index == 0) {
                            "gardeninvitecapacityleader@example.com"
                        } else {
                            "gardeninvitecapacity$index@example.com"
                        },
                        userNick = if (index == 0) "capacity-leader" else "capacity$index",
                    ),
                ).userNo,
            )
            gardenUserRepository.save(
                GardenUserEntity(
                    gardenNo = gardenNo,
                    userNo = memberNo,
                    gardenLeader = index == 0,
                    gardenMain = index == 0,
                ),
            )
        }

        mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $joinerToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 멤버 초과"))

        assertEquals(10L, gardenUserRepository.countByGardenNo(gardenNo))
        assertEquals(null, gardenUserRepository.findByGardenNoAndUserNo(gardenNo, joinerNo))
        verifyNoInteractions(pushService)
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
        isLeader: Boolean,
        isMain: Boolean,
        signDate: LocalDateTime = LocalDateTime.of(2026, 4, 6, 10, 0, 0),
    ): Int {
        val garden = gardenRepository.save(
            GardenEntity(
                gardenTitle = title,
                gardenInfo = "$title 소개",
                gardenColor = "green",
            ),
        )
        val gardenNo = checkNotNull(garden.gardenNo)
        gardenUserRepository.save(
            GardenUserEntity(
                gardenNo = gardenNo,
                userNo = userNo,
                gardenLeader = isLeader,
                gardenMain = isMain,
                gardenSignDate = signDate,
            ),
        )
        return gardenNo
    }

    private fun createStoredImage(relativePath: String) {
        val storedPath = imagesRoot.resolve(relativePath)
        Files.createDirectories(checkNotNull(storedPath.parent))
        Files.writeString(storedPath, "image-bytes")
    }

    private fun std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity.copyForGardenTest(
        userNo: Int? = this.userNo,
        userEmail: String = this.userEmail,
        userPassword: String = this.userPassword,
        userCreatedAt: LocalDateTime = this.userCreatedAt,
        userNick: String = this.userNick,
        userImage: String = this.userImage,
        userFcm: String = this.userFcm,
        userSocialId: String = this.userSocialId,
        userSocialType: String = this.userSocialType,
        userAuthNumber: String? = this.userAuthNumber,
    ) = std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity(
        userNo = userNo,
        userEmail = userEmail,
        userPassword = userPassword,
        userCreatedAt = userCreatedAt,
        userNick = userNick,
        userImage = userImage,
        userFcm = userFcm,
        userSocialId = userSocialId,
        userSocialType = userSocialType,
        userAuthNumber = userAuthNumber,
    )

    private fun removeSignupGarden(userNo: Int) {
        gardenUserRepository.findAllByUserNo(userNo).forEach { membership ->
            gardenUserRepository.delete(membership)
            gardenRepository.deleteById(membership.gardenNo)
        }
    }
}
