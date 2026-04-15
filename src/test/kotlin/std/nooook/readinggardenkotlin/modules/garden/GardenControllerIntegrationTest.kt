package std.nooook.readinggardenkotlin.modules.garden

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
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
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.garden.service.GardenCommandService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
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
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val gardenMemberRepository: GardenMemberRepository,
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
            registry.add("spring.servlet.multipart.location") { imagesRoot.resolve("multipart-temp").toString() }
        }
    }

    @MockitoSpyBean
    private lateinit var pushService: PushService

    @MockitoBean
    private lateinit var fcmClient: FcmClient

    @MockitoSpyBean
    private lateinit var imageStorage: ImageStorage

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        cleanImagesRoot()
        refreshTokenRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookRepository.deleteAll()
        gardenMemberRepository.deleteAll()
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
        val user = checkNotNull(userRepository.findByEmail("gardencreate@example.com"))
        val userNo = user.id
        val previousMembershipCount = gardenMemberRepository.countByUserId(userNo)

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
        val gardenNo = body.path("data").path("garden_no").asLong()
        val savedGarden = checkNotNull(gardenRepository.findById(gardenNo).orElse(null))
        val memberships = gardenMemberRepository.findAllByUserId(userNo)
        val newMembership = memberships.single { it.garden.id == gardenNo }

        assertEquals("두 번째 가든", savedGarden.title)
        assertEquals("함께 읽기", savedGarden.info)
        assertEquals("yellow", savedGarden.color)
        assertEquals(previousMembershipCount + 1, gardenMemberRepository.countByUserId(userNo))
        assertTrue(newMembership.isLeader)
        assertTrue(newMembership.isMain)
    }

    @Test
    fun `create garden should reject when user already has five gardens`() {
        val accessToken = signupAndGetAccessToken("gardenlimit@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenlimit@example.com"))
        val userNo = user.id

        repeat(4) { index ->
            val garden = gardenRepository.save(
                GardenEntity(
                    title = "추가 가든 ${index + 1}",
                    info = "소개 ${index + 1}",
                    color = "green",
                ),
            )
            gardenMemberRepository.save(
                GardenMemberEntity(
                    garden = garden,
                    user = user,
                    isLeader = true,
                    isMain = true,
                ),
            )
        }

        val beforeGardenCount = gardenRepository.count()
        val beforeMembershipCount = gardenMemberRepository.countByUserId(userNo)

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
        assertEquals(beforeMembershipCount, gardenMemberRepository.countByUserId(userNo))
        assertFalse(gardenRepository.findAll().any { it.title == "초과 가든" })
    }

    @Test
    fun `create garden should keep five garden limit under concurrent requests`() {
        signupAndGetAccessToken("gardenrace@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenrace@example.com"))
        val userNo = user.id

        repeat(3) { index ->
            val garden = gardenRepository.save(
                GardenEntity(
                    title = "기존 가든 ${index + 1}",
                    info = "소개 ${index + 1}",
                    color = "green",
                ),
            )
            gardenMemberRepository.save(
                GardenMemberEntity(
                    garden = garden,
                    user = user,
                    isLeader = true,
                    isMain = true,
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
                            userId = userNo,
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
            assertEquals(5L, gardenMemberRepository.countByUserId(userNo))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `garden list should return callers main garden first with legacy fields`() {
        val accessToken = signupAndGetAccessToken("gardenlist@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenlist@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val secondaryGarden = gardenRepository.save(
            GardenEntity(
                title = "서브 가든",
                info = "두 번째",
                color = "blue",
                createdAt = LocalDateTime.of(2024, 1, 10, 9, 0, 0),
            ),
        )
        val mainGarden = gardenRepository.save(
            GardenEntity(
                title = "메인 가든",
                info = "첫 번째",
                color = "green",
                createdAt = LocalDateTime.of(2024, 1, 5, 8, 30, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = secondaryGarden,
                user = user,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 1, 11, 10, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = mainGarden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 1, 6, 7, 0, 0),
            ),
        )
        val teammate = userRepository.save(
            userRepository.findById(userNo).orElse(null)?.copyForGardenTest(
                userId = null,
                email = "gardenlist-teammate@example.com",
                nick = "teammate",
            ) ?: error("User fixture missing"),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = mainGarden,
                user = teammate,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 1, 7, 7, 0, 0),
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = mainGarden,
                title = "메인 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 100,
                isbn = "9781111111111",
                tree = "소설",
                imageUrl = "https://example.com/main.jpg",
                info = "메인 책 소개",
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = secondaryGarden,
                title = "서브 책",
                author = "저자2",
                publisher = "출판사2",
                status = 0,
                user = user,
                page = 200,
                isbn = "9782222222222",
                tree = "에세이",
                imageUrl = "https://example.com/sub.jpg",
                info = "서브 책 소개",
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 리스트 조회 성공"))
            .andExpect(jsonPath("$.data[0].garden_no").value(checkNotNull(mainGarden.id)))
            .andExpect(jsonPath("$.data[0].garden_title").value("메인 가든"))
            .andExpect(jsonPath("$.data[0].garden_info").value("첫 번째"))
            .andExpect(jsonPath("$.data[0].garden_color").value("green"))
            .andExpect(jsonPath("$.data[0].garden_members").value(2))
            .andExpect(jsonPath("$.data[0].book_count").value(1))
            .andExpect(jsonPath("$.data[0].garden_created_at").value("2024-01-05T08:30:00"))
            .andExpect(jsonPath("$.data[1].garden_no").value(checkNotNull(secondaryGarden.id)))
            .andExpect(jsonPath("$.data[1].garden_members").value(1))
            .andExpect(jsonPath("$.data[1].book_count").value(1))
    }

    @Test
    fun `garden detail should return legacy shape with leader first and latest percent`() {
        val accessToken = signupAndGetAccessToken("gardendetail@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendetail@example.com"))
        val userNo = checkNotNull(user.id)
        removeSignupGarden(userNo)
        val garden = gardenRepository.save(
            GardenEntity(
                title = "상세 가든",
                info = "상세 설명",
                color = "orange",
                createdAt = LocalDateTime.of(2024, 2, 1, 12, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 2, 1, 12, 5, 0),
            ),
        )
        val member = userRepository.save(
            userRepository.findById(userNo).orElse(null)?.copyForGardenTest(
                userId = null,
                email = "gardendetail-member@example.com",
                nick = "member",
                image = "member.png",
            ) ?: error("User fixture missing"),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = member,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 2, 2, 9, 0, 0),
            ),
        )
        val book = bookRepository.save(
            BookEntity(
                garden = garden,
                title = "가든 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 400,
                isbn = "9783333333333",
                tree = "인문",
                imageUrl = "https://example.com/detail.jpg",
                info = "가든 책 소개",
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = book,
                currentPage = 120,
                createdAt = LocalDateTime.of(2024, 2, 2, 10, 0, 0),
                
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = book,
                currentPage = 200,
                createdAt = LocalDateTime.of(2024, 2, 3, 10, 0, 0),
                
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.id).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 상세 조회 성공"))
            .andExpect(jsonPath("$.data.garden_no").value(checkNotNull(garden.id)))
            .andExpect(jsonPath("$.data.garden_title").value("상세 가든"))
            .andExpect(jsonPath("$.data.garden_info").value("상세 설명"))
            .andExpect(jsonPath("$.data.garden_color").value("orange"))
            .andExpect(jsonPath("$.data.garden_created_at").value("2024-02-01T12:00:00"))
            .andExpect(jsonPath("$.data.book_list[0].book_no").value(checkNotNull(book.id)))
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
            .andExpect(jsonPath("$.data.garden_members[0].user_nick").value(user.nick))
            .andExpect(jsonPath("$.data.garden_members[0].user_image").value(user.image))
            .andExpect(jsonPath("$.data.garden_members[0].garden_leader").value(true))
            .andExpect(jsonPath("$.data.garden_members[0].garden_sign_date").value("2024-02-01T12:05:00"))
            .andExpect(jsonPath("$.data.garden_members[1].user_no").value(checkNotNull(member.id)))
            .andExpect(jsonPath("$.data.garden_members[1].user_nick").value("member"))
            .andExpect(jsonPath("$.data.garden_members[1].user_image").value("member.png"))
            .andExpect(jsonPath("$.data.garden_members[1].garden_leader").value(false))
            .andExpect(jsonPath("$.data.garden_members[1].garden_sign_date").value("2024-02-02T09:00:00"))
    }

    @Test
    fun `garden detail should keep null legacy book fields and zero percent for non positive page`() {
        val accessToken = signupAndGetAccessToken("gardennulls@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardennulls@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)
        val garden = gardenRepository.save(
            GardenEntity(
                title = "널 가든",
                info = "널 필드 확인",
                color = "white",
                createdAt = LocalDateTime.of(2024, 3, 1, 9, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 3, 1, 9, 5, 0),
            ),
        )
        val book = bookRepository.save(
            BookEntity(
                garden = garden,
                title = "페이지 0 책",
                author = "작가",
                publisher = "출판사",
                status = 0,
                user = user,
                page = 0,
                isbn = null,
                tree = null,
                imageUrl = null,
                info = "널과 0 확인",
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = book,
                currentPage = 10,
                createdAt = LocalDateTime.of(2024, 3, 2, 9, 0, 0),
                
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.id).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.book_list[0].book_no").value(checkNotNull(book.id)))
            .andExpect(jsonPath("$.data.book_list[0].book_isbn").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].book_image_url").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].book_tree").doesNotExist())
            .andExpect(jsonPath("$.data.book_list[0].percent").value(0.0))
            .andExpect(jsonPath("$.data.book_list[0].book_page").value(0))
    }

    @Test
    fun `garden detail should reject when caller is not a member`() {
        val ownerToken = signupAndGetAccessToken("gardenowner@example.com")
        val owner = checkNotNull(userRepository.findByEmail("gardenowner@example.com"))
        val ownerNo = owner.id
        removeSignupGarden(ownerNo)
        val outsiderToken = signupAndGetAccessToken("gardenoutsider@example.com")
        val outsiderNo = checkNotNull(userRepository.findByEmail("gardenoutsider@example.com")?.id)
        removeSignupGarden(outsiderNo)
        val garden = gardenRepository.save(
            GardenEntity(
                title = "비공개 가든",
                info = "소속 제한",
                color = "black",
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = owner,
                isLeader = true,
                isMain = true,
            ),
        )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .queryParam("garden_no", checkNotNull(garden.id).toString()),
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $outsiderToken")
                .queryParam("garden_no", checkNotNull(garden.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }

    @Test
    fun `update garden should require caller to be leader`() {
        val ownerToken = signupAndGetAccessToken("gardenupdateowner@example.com")
        val owner = checkNotNull(userRepository.findByEmail("gardenupdateowner@example.com"))
        val ownerNo = owner.id
        removeSignupGarden(ownerNo)
        val memberToken = signupAndGetAccessToken("gardenupdatemember@example.com")
        val memberUser = checkNotNull(userRepository.findByEmail("gardenupdatemember@example.com"))
        val memberNo = memberUser.id
        removeSignupGarden(memberNo)

        val garden = gardenRepository.save(
            GardenEntity(
                title = "수정 전 가든",
                info = "수정 전 소개",
                color = "green",
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = owner,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = memberUser,
                isLeader = false,
                isMain = false,
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
        assertEquals("수정 전 가든", unchangedGarden.title)
        assertEquals("수정 전 소개", unchangedGarden.info)
        assertEquals("green", unchangedGarden.color)

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
        assertEquals("리더 수정", updatedGarden.title)
        assertEquals("리더만 가능", updatedGarden.info)
        assertEquals("red", updatedGarden.color)
    }

    @Test
    fun `delete garden should forbid shared garden even for leader`() {
        val leaderToken = signupAndGetAccessToken("gardendeletesharedleader@example.com")
        val leaderNo = checkNotNull(userRepository.findByEmail("gardendeletesharedleader@example.com")?.id)
        removeSignupGarden(leaderNo)
        val teammateToken = signupAndGetAccessToken("gardendeletesharedmate@example.com")
        val teammateNo = checkNotNull(userRepository.findByEmail("gardendeletesharedmate@example.com")?.id)
        val teammateUser = checkNotNull(userRepository.findById(teammateNo).orElse(null))
        removeSignupGarden(teammateNo)

        val sharedGardenNo = createGardenMembership(
            userId = leaderNo,
            title = "공유 가든",
            isLeader = true,
            isMain = false,
        )
        val sharedGarden = gardenRepository.findById(sharedGardenNo).orElseThrow()
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = sharedGarden,
                user = teammateUser,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2026, 4, 6, 10, 5, 0),
            ),
        )
        createGardenMembership(
            userId = leaderNo,
            title = "남아야 할 가든",
            isLeader = true,
            isMain = true,
        )
        createGardenMembership(
            userId = teammateNo,
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
        assertEquals(2L, gardenMemberRepository.countByGardenId(sharedGardenNo))
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(sharedGardenNo, leaderNo)?.isLeader == true)

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
        val memberNo = checkNotNull(userRepository.findByEmail("gardendeletenonleader@example.com")?.id)
        removeSignupGarden(memberNo)

        createGardenMembership(
            userId = memberNo,
            title = "남아야 할 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userId = memberNo,
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
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, memberNo)?.isLeader == false)
        assertEquals(1L, gardenMemberRepository.countByGardenId(targetGardenNo))
        assertEquals(2L, gardenMemberRepository.countByUserId(memberNo))
    }

    @Test
    fun `delete garden should delete owned resources and image artifacts`() {
        val accessToken = signupAndGetAccessToken("gardendeletesolo@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendeletesolo@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        createGardenMembership(
            userId = userNo,
            title = "유지 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userId = userNo,
            title = "삭제 대상 가든",
            isLeader = true,
            isMain = false,
        )
        val targetGardenEntity = gardenRepository.findById(targetGardenNo).orElseThrow()

        val ownedBook = bookRepository.save(
            BookEntity(
                garden = targetGardenEntity,
                title = "삭제 대상 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 300,
                isbn = "9787600000000",
                tree = "seed",
                info = "삭제될 책",
            ),
        )
        val ownedBookNo = checkNotNull(ownedBook.id)
        bookReadRepository.save(
            BookReadEntity(
                book = ownedBook,
                currentPage = 123,
                
            ),
        )

        val bookImagePath = "garden/delete/book-image.png"
        createStoredImage(bookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                book = ownedBook,
                name = "book-image.png",
                url = bookImagePath,
            ),
        )

        val memo = memoRepository.save(
            MemoEntity(
                book = ownedBook,
                content = "삭제될 메모",
                user = user,
                isLiked = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        val memoImagePath = "garden/delete/memo-image.png"
        createStoredImage(memoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memo = memo,
                name = "memo-image.png",
                url = memoImagePath,
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
        assertEquals(null, gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, userNo))
        assertFalse(bookRepository.findById(ownedBookNo).isPresent)
        assertTrue(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(ownedBookNo).isEmpty())
        assertTrue(bookImageRepository.findAllByBookId(ownedBookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookId(ownedBookNo).isEmpty())
        assertTrue(memoImageRepository.findAllByMemoIdIn(listOf(memoNo)).isEmpty())
        assertFalse(Files.exists(imagesRoot.resolve(bookImagePath)))
        assertFalse(Files.exists(imagesRoot.resolve(memoImagePath)))
    }

    @Test
    fun `delete garden should roll back staged image cleanup when later stage delete fails`() {
        val accessToken = signupAndGetAccessToken("gardendeleterollback@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendeleterollback@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        createGardenMembership(
            userId = userNo,
            title = "유지 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userId = userNo,
            title = "롤백 대상 가든",
            isLeader = true,
            isMain = false,
        )
        val targetGardenEntity = gardenRepository.findById(targetGardenNo).orElseThrow()

        val ownedBook = bookRepository.save(
            BookEntity(
                garden = targetGardenEntity,
                title = "롤백 대상 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 280,
                isbn = "9787600000009",
                tree = "seed",
                info = "롤백 확인용 책",
            ),
        )
        val ownedBookNo = checkNotNull(ownedBook.id)
        bookReadRepository.save(
            BookReadEntity(
                book = ownedBook,
                currentPage = 111,
                
            ),
        )

        val bookImagePath = "garden/rollback/book-image.png"
        createStoredImage(bookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                book = ownedBook,
                name = "book-image.png",
                url = bookImagePath,
            ),
        )

        val memo = memoRepository.save(
            MemoEntity(
                book = ownedBook,
                content = "롤백 대상 메모",
                user = user,
                isLiked = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        val memoImagePath = "garden/rollback/memo-image.png"
        createStoredImage(memoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memo = memo,
                name = "memo-image.png",
                url = memoImagePath,
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
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, userNo) != null)
        assertTrue(bookRepository.findById(ownedBookNo).isPresent)
        assertEquals(1, bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(ownedBookNo).size)
        assertEquals(1, bookImageRepository.findAllByBookId(ownedBookNo).size)
        assertEquals(1, memoRepository.findAllByBookId(ownedBookNo).size)
        assertEquals(1, memoImageRepository.findAllByMemoIdIn(listOf(memoNo)).size)
        assertTrue(Files.exists(imagesRoot.resolve(bookImagePath)))
        assertTrue(Files.exists(imagesRoot.resolve(memoImagePath)))
    }

    @Test
    fun `delete garden should forbid when caller would lose last membership`() {
        val accessToken = signupAndGetAccessToken("gardendeletelast@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendeletelast@example.com"))
        val userNo = user.id
        val targetMembership = gardenMemberRepository.findAllByUserId(userNo).single()

        mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetMembership.id.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 불가"))

        assertTrue(gardenRepository.existsById(targetMembership.id))
        assertEquals(1L, gardenMemberRepository.countByUserId(userNo))
    }

    @Test
    fun `leave garden member should delete owned resources and transfer leader to earliest member`() {
        val leaderToken = signupAndGetAccessToken("gardenleaveleader@example.com")
        val leaderUser = checkNotNull(userRepository.findByEmail("gardenleaveleader@example.com"))
        val leaderNo = leaderUser.id
        removeSignupGarden(leaderNo)

        val earliestMember = userRepository.save(
            checkNotNull(userRepository.findById(leaderNo).orElse(null)).copyForGardenTest(
                userId = null,
                email = "gardenleaveearliest@example.com",
                nick = "earliest-member",
            ),
        )
        val laterMember = userRepository.save(
            checkNotNull(userRepository.findById(leaderNo).orElse(null)).copyForGardenTest(
                userId = null,
                email = "gardenleavelater@example.com",
                nick = "later-member",
            ),
        )

        createGardenMembership(
            userId = leaderNo,
            title = "남는 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "탈퇴 대상 가든",
                info = "리더 위임",
                color = "green",
            ),
        )
        val targetGardenNo = checkNotNull(targetGardenEntity.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = targetGardenEntity,
                user = leaderUser,
                isLeader = true,
                isMain = false,
                joinDate = LocalDateTime.of(2026, 4, 6, 9, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = targetGardenEntity,
                user = earliestMember,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2026, 4, 6, 9, 1, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = targetGardenEntity,
                user = laterMember,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2026, 4, 6, 9, 2, 0),
            ),
        )

        val leaderBook = bookRepository.save(
            BookEntity(
                garden = targetGardenEntity,
                title = "리더 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = leaderUser,
                page = 320,
                isbn = "9787610000000",
                tree = "sprout",
                info = "리더 책 설명",
            ),
        )
        val leaderBookNo = checkNotNull(leaderBook.id)
        bookReadRepository.save(
            BookReadEntity(
                book = leaderBook,
                currentPage = 200,
                
            ),
        )

        val leaderBookImagePath = "garden/leave/leader-book.png"
        createStoredImage(leaderBookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                book = leaderBook,
                name = "leader-book.png",
                url = leaderBookImagePath,
            ),
        )

        val leaderMemo = memoRepository.save(
            MemoEntity(
                book = leaderBook,
                content = "리더 메모",
                user = leaderUser,
                isLiked = true,
            ),
        )
        val leaderMemoNo = checkNotNull(leaderMemo.id)

        val leaderMemoImagePath = "garden/leave/leader-memo.png"
        createStoredImage(leaderMemoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memo = leaderMemo,
                name = "leader-memo.png",
                url = leaderMemoImagePath,
            ),
        )

        val teammateBook = bookRepository.save(
            BookEntity(
                garden = targetGardenEntity,
                title = "남는 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = earliestMember,
                page = 210,
                isbn = "9787610000001",
                tree = "leaf",
                info = "팀원 책 설명",
            ),
        )
        val teammateBookNo = checkNotNull(teammateBook.id)

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $leaderToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 성공"))

        assertEquals(null, gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, leaderNo))
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, checkNotNull(earliestMember.id))?.isLeader == true)
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, checkNotNull(laterMember.id))?.isLeader == false)
        assertTrue(gardenRepository.existsById(targetGardenNo))
        assertFalse(bookRepository.findById(leaderBookNo).isPresent)
        assertTrue(bookRepository.findById(teammateBookNo).isPresent)
        assertTrue(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(leaderBookNo).isEmpty())
        assertTrue(bookImageRepository.findAllByBookId(leaderBookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookId(leaderBookNo).isEmpty())
        assertTrue(memoImageRepository.findAllByMemoIdIn(listOf(leaderMemoNo)).isEmpty())
        assertFalse(Files.exists(imagesRoot.resolve(leaderBookImagePath)))
        assertFalse(Files.exists(imagesRoot.resolve(leaderMemoImagePath)))
    }

    @Test
    fun `leave garden member should roll back staged image cleanup when later stage delete fails`() {
        val leaderToken = signupAndGetAccessToken("gardenleaverollback@example.com")
        val leaderUser = checkNotNull(userRepository.findByEmail("gardenleaverollback@example.com"))
        val leaderNo = leaderUser.id
        removeSignupGarden(leaderNo)

        val otherMember = userRepository.save(
            checkNotNull(userRepository.findById(leaderNo).orElse(null)).copyForGardenTest(
                userId = null,
                email = "gardenleaverollback-member@example.com",
                nick = "rollback-member",
            ),
        )

        createGardenMembership(
            userId = leaderNo,
            title = "남는 가든",
            isLeader = true,
            isMain = true,
        )
        val targetGardenNo = createGardenMembership(
            userId = leaderNo,
            title = "탈퇴 롤백 가든",
            isLeader = true,
            isMain = false,
            signDate = LocalDateTime.of(2026, 4, 6, 8, 59, 0),
        )
        val targetGardenEntity = gardenRepository.findById(targetGardenNo).orElseThrow()
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = targetGardenEntity,
                user = otherMember,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2026, 4, 6, 9, 1, 0),
            ),
        )

        val leaderBook = bookRepository.save(
            BookEntity(
                garden = targetGardenEntity,
                title = "탈퇴 롤백 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = leaderUser,
                page = 320,
                isbn = "9787610000010",
                tree = "sprout",
                info = "탈퇴 롤백 확인용 책",
            ),
        )
        val leaderBookNo = checkNotNull(leaderBook.id)
        bookReadRepository.save(
            BookReadEntity(
                book = leaderBook,
                currentPage = 144,
                
            ),
        )

        val leaderBookImagePath = "garden/leave-rollback/leader-book.png"
        createStoredImage(leaderBookImagePath)
        bookImageRepository.save(
            BookImageEntity(
                book = leaderBook,
                name = "leader-book.png",
                url = leaderBookImagePath,
            ),
        )

        val leaderMemo = memoRepository.save(
            MemoEntity(
                book = leaderBook,
                content = "탈퇴 롤백 메모",
                user = leaderUser,
                isLiked = false,
            ),
        )
        val leaderMemoNo = checkNotNull(leaderMemo.id)

        val leaderMemoImagePath = "garden/leave-rollback/leader-memo.png"
        createStoredImage(leaderMemoImagePath)
        memoImageRepository.save(
            MemoImageEntity(
                memo = leaderMemo,
                name = "leader-memo.png",
                url = leaderMemoImagePath,
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
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, leaderNo)?.isLeader == true)
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, checkNotNull(otherMember.id))?.isLeader == false)
        assertTrue(bookRepository.findById(leaderBookNo).isPresent)
        assertEquals(1, bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(leaderBookNo).size)
        assertEquals(1, bookImageRepository.findAllByBookId(leaderBookNo).size)
        assertEquals(1, memoRepository.findAllByBookId(leaderBookNo).size)
        assertEquals(1, memoImageRepository.findAllByMemoIdIn(listOf(leaderMemoNo)).size)
        assertTrue(Files.exists(imagesRoot.resolve(leaderBookImagePath)))
        assertTrue(Files.exists(imagesRoot.resolve(leaderMemoImagePath)))
    }

    @Test
    fun `leave garden member should forbid when garden has one member`() {
        val accessToken = signupAndGetAccessToken("gardenleavealone@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenleavealone@example.com"))
        val userNo = user.id
        val targetMembership = gardenMemberRepository.findAllByUserId(userNo).single()

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetMembership.id.toString()),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 불가"))

        assertTrue(gardenRepository.existsById(targetMembership.id))
        assertEquals(1L, gardenMemberRepository.countByGardenId(targetMembership.id))
    }

    @Test
    fun `leave garden member should forbid non member`() {
        val memberToken = signupAndGetAccessToken("gardenleavemember@example.com")
        val memberNo = checkNotNull(userRepository.findByEmail("gardenleavemember@example.com")?.id)
        removeSignupGarden(memberNo)
        val outsiderToken = signupAndGetAccessToken("gardenleaveoutsider@example.com")
        val outsiderNo = checkNotNull(userRepository.findByEmail("gardenleaveoutsider@example.com")?.id)
        removeSignupGarden(outsiderNo)

        val targetGardenNo = createGardenMembership(
            userId = memberNo,
            title = "멤버 가든",
            isLeader = true,
            isMain = true,
        )
        createGardenMembership(
            userId = outsiderNo,
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
        assertEquals(1L, gardenMemberRepository.countByGardenId(targetGardenNo))
        assertEquals(null, gardenMemberRepository.findByGardenIdAndUserId(targetGardenNo, outsiderNo))
    }

    @Test
    fun `move garden should reject when destination would exceed thirty books`() {
        val accessToken = signupAndGetAccessToken("gardenmovelimit@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmovelimit@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val sourceGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "출발 가든",
                info = "옮기기 전",
                color = "blue",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                title = "도착 가든",
                info = "옮기기 후",
                color = "yellow",
            ),
        )
        val sourceGardenNo = checkNotNull(sourceGardenEntity.id)
        val destinationGardenNo = checkNotNull(destinationGarden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = sourceGardenEntity,
                user = user,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = destinationGarden,
                user = user,
                isLeader = true,
                isMain = false,
            ),
        )

        repeat(2) { index ->
            bookRepository.save(
                BookEntity(
                    garden = sourceGardenEntity,
                    title = "이동 대상 ${index + 1}",
                    author = "작가",
                    publisher = "출판사",
                    status = 1,
                    user = user,
                    page = 100,
                    isbn = "978700000000$index",
                    tree = "소설",
                    imageUrl = null,
                    info = "출발 가든 책",
                ),
            )
        }
        repeat(29) { index ->
            bookRepository.save(
                BookEntity(
                    garden = destinationGarden,
                    title = "도착 책 ${index + 1}",
                    author = "작가",
                    publisher = "출판사",
                    status = 1,
                    user = user,
                    page = 100,
                    isbn = "9787100000${index.toString().padStart(3, '0')}",
                    tree = "소설",
                    imageUrl = null,
                    info = "도착 가든 책",
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

        assertEquals(2, bookRepository.findAllByUserIdAndGardenId(userNo, sourceGardenNo).size)
        assertEquals(29, bookRepository.findAllByGardenIdOrderByIdAsc(destinationGardenNo).size)
    }

    @Test
    fun `move garden should move only caller owned books`() {
        val accessToken = signupAndGetAccessToken("gardenmoveowned@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmoveowned@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)
        val teammate = userRepository.save(
            userRepository.findById(userNo).orElse(null)?.copyForGardenTest(
                userId = null,
                email = "gardenmoveownedteammate@example.com",
                nick = "teammate-move",
            ) ?: error("User fixture missing"),
        )

        val sourceGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "출발 가든",
                info = "내 책만 이동",
                color = "purple",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                title = "도착 가든",
                info = "도착",
                color = "orange",
            ),
        )
        val sourceGardenNo = checkNotNull(sourceGardenEntity.id)
        val destinationGardenNo = checkNotNull(destinationGarden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = sourceGardenEntity,
                user = user,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = destinationGarden,
                user = user,
                isLeader = false,
                isMain = false,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = sourceGardenEntity,
                user = teammate,
                isLeader = false,
                isMain = false,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = destinationGarden,
                user = teammate,
                isLeader = false,
                isMain = false,
            ),
        )

        val callerBook = bookRepository.save(
            BookEntity(
                garden = sourceGardenEntity,
                title = "내 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 100,
                isbn = "9787200000000",
                tree = "소설",
                imageUrl = null,
                info = "내 책 설명",
            ),
        )
        val teammateBook = bookRepository.save(
            BookEntity(
                garden = sourceGardenEntity,
                title = "팀원 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = teammate,
                page = 100,
                isbn = "9787200000001",
                tree = "소설",
                imageUrl = null,
                info = "팀원 책 설명",
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

        assertEquals(destinationGardenNo, bookRepository.findById(checkNotNull(callerBook.id)).orElseThrow().id)
        assertEquals(sourceGardenNo, bookRepository.findById(checkNotNull(teammateBook.id)).orElseThrow().id)
    }

    @Test
    fun `move garden should keep thirty book limit under concurrent moves to same destination`() {
        signupAndGetAccessToken("gardenmovelock@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmovelock@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val sourceGardenA = gardenRepository.save(
            GardenEntity(
                title = "출발 가든 A",
                info = "동시 이동 A",
                color = "blue",
            ),
        )
        val sourceGardenB = gardenRepository.save(
            GardenEntity(
                title = "출발 가든 B",
                info = "동시 이동 B",
                color = "purple",
            ),
        )
        val destinationGarden = gardenRepository.save(
            GardenEntity(
                title = "도착 가든",
                info = "동시 도착지",
                color = "yellow",
            ),
        )
        val sourceGardenANo = checkNotNull(sourceGardenA.id)
        val sourceGardenBNo = checkNotNull(sourceGardenB.id)
        val destinationGardenNo = checkNotNull(destinationGarden.id)

        listOf(sourceGardenA, sourceGardenB, destinationGarden).forEachIndexed { index, gardenEntity ->
            gardenMemberRepository.save(
                GardenMemberEntity(
                    garden = gardenEntity,
                    user = user,
                    isLeader = index == 0,
                    isMain = index == 0,
                ),
            )
        }

        repeat(15) { index ->
            bookRepository.save(
                BookEntity(
                    garden = sourceGardenA,
                    title = "A 책 ${index + 1}",
                    author = "작가",
                    publisher = "출판사",
                    status = 1,
                    user = user,
                    page = 100,
                    isbn = "9787300000${index.toString().padStart(3, '0')}",
                    tree = "소설",
                    info = "A 출발 책",
                ),
            )
        }
        repeat(15) { index ->
            bookRepository.save(
                BookEntity(
                    garden = sourceGardenB,
                    title = "B 책 ${index + 1}",
                    author = "작가",
                    publisher = "출판사",
                    status = 1,
                    user = user,
                    page = 100,
                    isbn = "9787310000${index.toString().padStart(3, '0')}",
                    tree = "소설",
                    info = "B 출발 책",
                ),
            )
        }
        repeat(10) { index ->
            bookRepository.save(
                BookEntity(
                    garden = destinationGarden,
                    title = "도착 책 ${index + 1}",
                    author = "작가",
                    publisher = "출판사",
                    status = 1,
                    user = user,
                    page = 100,
                    isbn = "9787320000${index.toString().padStart(3, '0')}",
                    tree = "소설",
                    info = "도착 책",
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
            assertEquals(25, bookRepository.findAllByGardenIdOrderByIdAsc(destinationGardenNo).size)
            assertTrue(
                setOf(
                    bookRepository.findAllByUserIdAndGardenId(userNo, sourceGardenANo).size,
                    bookRepository.findAllByUserIdAndGardenId(userNo, sourceGardenBNo).size,
                ).contains(15),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `update garden main should keep only one main membership`() {
        val accessToken = signupAndGetAccessToken("gardenmainswitch@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmainswitch@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val mainGarden = gardenRepository.save(
            GardenEntity(
                title = "기존 메인",
                info = "원래 메인",
                color = "green",
            ),
        )
        val targetGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "새 메인",
                info = "바꿀 메인",
                color = "red",
            ),
        )
        val mainGardenNo = checkNotNull(mainGarden.id)
        val targetGardenNo = checkNotNull(targetGardenEntity.id)
        val currentMainMembership = gardenMemberRepository.save(
            GardenMemberEntity(
                garden = mainGarden,
                user = user,
                isLeader = true,
                isMain = true,
            ),
        )
        val targetMembership = gardenMemberRepository.save(
            GardenMemberEntity(
                garden = targetGardenEntity,
                user = user,
                isLeader = false,
                isMain = false,
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

        val refreshedCurrentMain = gardenMemberRepository.findById(checkNotNull(currentMainMembership.id)).orElseThrow()
        val refreshedTargetMembership = gardenMemberRepository.findById(checkNotNull(targetMembership.id)).orElseThrow()
        val mainMemberships = gardenMemberRepository.findAllByUserId(userNo).filter { it.isMain }

        assertFalse(refreshedCurrentMain.isMain)
        assertTrue(refreshedTargetMembership.isMain)
        assertEquals(1, mainMemberships.size)
        assertEquals(targetGardenNo, mainMemberships.single().id)
    }

    @Test
    fun `update garden main should keep one main membership under concurrent switches`() {
        signupAndGetAccessToken("gardenmainlock@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmainlock@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val originalMainGarden = gardenRepository.save(
            GardenEntity(
                title = "원래 메인",
                info = "초기 메인",
                color = "green",
            ),
        )
        val candidateGardenA = gardenRepository.save(
            GardenEntity(
                title = "후보 A",
                info = "첫 번째 후보",
                color = "blue",
            ),
        )
        val candidateGardenB = gardenRepository.save(
            GardenEntity(
                title = "후보 B",
                info = "두 번째 후보",
                color = "red",
            ),
        )
        val originalMainGardenNo = checkNotNull(originalMainGarden.id)
        val candidateGardenANo = checkNotNull(candidateGardenA.id)
        val candidateGardenBNo = checkNotNull(candidateGardenB.id)

        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = originalMainGarden,
                user = user,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = candidateGardenA,
                user = user,
                isLeader = false,
                isMain = false,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = candidateGardenB,
                user = user,
                isLeader = false,
                isMain = false,
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

            val mainMemberships = gardenMemberRepository.findAllByUserId(userNo).filter { it.isMain }
            assertEquals(1, mainMemberships.size)
            assertTrue(mainMemberships.single().id == candidateGardenANo || mainMemberships.single().id == candidateGardenBNo)
            assertFalse(gardenMemberRepository.findByGardenIdAndUserId(originalMainGardenNo, userNo)?.isMain ?: true)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `update garden member should transfer leader only to same garden member`() {
        val leaderToken = signupAndGetAccessToken("gardenleadertransfer@example.com")
        val leaderUser = checkNotNull(userRepository.findByEmail("gardenleadertransfer@example.com"))
        val leaderNo = leaderUser.id
        removeSignupGarden(leaderNo)
        val targetToken = signupAndGetAccessToken("gardentargettransfer@example.com")
        val targetUser = checkNotNull(userRepository.findByEmail("gardentargettransfer@example.com"))
        val targetNo = targetUser.id
        removeSignupGarden(targetNo)
        val outsiderToken = signupAndGetAccessToken("gardenoutsidertransfer@example.com")
        val outsiderNo = checkNotNull(userRepository.findByEmail("gardenoutsidertransfer@example.com")?.id)
        removeSignupGarden(outsiderNo)

        val garden = gardenRepository.save(
            GardenEntity(
                title = "리더 위임 가든",
                info = "대표 이전 확인",
                color = "green",
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = leaderUser,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = targetUser,
                isLeader = false,
                isMain = false,
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

        assertFalse(gardenMemberRepository.findByGardenIdAndUserId(gardenNo, leaderNo)?.isLeader ?: true)
        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(gardenNo, targetNo)?.isLeader ?: false)

        mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $targetToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", leaderNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 멤버 변경 성공"))

        assertTrue(gardenMemberRepository.findByGardenIdAndUserId(gardenNo, leaderNo)?.isLeader ?: false)
        assertFalse(gardenMemberRepository.findByGardenIdAndUserId(gardenNo, targetNo)?.isLeader ?: true)
    }

    @Test
    fun `invite garden should join caller as non leader non main and notify existing members`() {
        val leaderToken = signupAndGetAccessToken("gardeninviteleader@example.com")
        val leaderUser = checkNotNull(userRepository.findByEmail("gardeninviteleader@example.com"))
        val leaderNo = leaderUser.id
        removeSignupGarden(leaderNo)
        val existingMemberToken = signupAndGetAccessToken("gardeninviteexisting@example.com")
        val existingMemberUser = checkNotNull(userRepository.findByEmail("gardeninviteexisting@example.com"))
        val existingMemberNo = existingMemberUser.id
        removeSignupGarden(existingMemberNo)
        val joinerToken = signupAndGetAccessToken("gardeninvitejoiner@example.com")
        val joinerNo = checkNotNull(userRepository.findByEmail("gardeninvitejoiner@example.com")?.id)
        removeSignupGarden(joinerNo)

        val garden = gardenRepository.save(
            GardenEntity(
                title = "초대 가든",
                info = "셀프 조인",
                color = "blue",
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = leaderUser,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = existingMemberUser,
                isLeader = false,
                isMain = false,
            ),
        )
        given(
            fcmClient.sendToMany(
                listOf("fcm-token"),
                "NEW 가드너 등장🧑‍🌾",
                "초대 가든에 새로운 멤버가 들어왔어요. 함께 책을 읽어 가든을 채워주세요",
                mapOf("garden_no" to gardenNo.toString()),
            ),
        ).willReturn(listOf(mapOf("result" to "sent")))

        mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $joinerToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("가든 초대 완료"))

        val joinedMembership = checkNotNull(gardenMemberRepository.findByGardenIdAndUserId(gardenNo, joinerNo))
        assertFalse(joinedMembership.isLeader)
        assertFalse(joinedMembership.isMain)
        assertEquals(3L, gardenMemberRepository.countByGardenId(gardenNo))
        verify(pushService).sendNewMemberPush(leaderNo, gardenNo)
        verify(pushService).sendNewMemberPush(existingMemberNo, gardenNo)
        verify(fcmClient, times(2)).sendToMany(
            listOf("fcm-token"),
            "NEW 가드너 등장🧑‍🌾",
            "초대 가든에 새로운 멤버가 들어왔어요. 함께 책을 읽어 가든을 채워주세요",
            mapOf("garden_no" to gardenNo.toString()),
        )
        verifyNoMoreInteractions(pushService)
    }

    @Test
    fun `invite garden should reject duplicate membership`() {
        val leaderToken = signupAndGetAccessToken("gardeninviteleaderduplicate@example.com")
        val leaderUser = checkNotNull(userRepository.findByEmail("gardeninviteleaderduplicate@example.com"))
        val leaderNo = leaderUser.id
        removeSignupGarden(leaderNo)
        val memberToken = signupAndGetAccessToken("gardeninvitememberduplicate@example.com")
        val memberUser = checkNotNull(userRepository.findByEmail("gardeninvitememberduplicate@example.com"))
        val memberNo = memberUser.id
        removeSignupGarden(memberNo)

        val garden = gardenRepository.save(
            GardenEntity(
                title = "중복 가든",
                info = "중복 가입 방지",
                color = "yellow",
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = leaderUser,
                isLeader = true,
                isMain = true,
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = memberUser,
                isLeader = false,
                isMain = false,
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

        assertEquals(2L, gardenMemberRepository.countByGardenId(gardenNo))
        verifyNoInteractions(pushService)
        verifyNoInteractions(fcmClient)
    }

    @Test
    fun `invite garden should reject when garden member capacity exceeds ten`() {
        val joinerToken = signupAndGetAccessToken("gardeninvitecapacityjoiner@example.com")
        val joinerNo = checkNotNull(userRepository.findByEmail("gardeninvitecapacityjoiner@example.com")?.id)
        removeSignupGarden(joinerNo)
        val seedUser = checkNotNull(userRepository.findById(joinerNo).orElse(null))

        val garden = gardenRepository.save(
            GardenEntity(
                title = "정원 초과 가든",
                info = "멤버 제한 확인",
                color = "purple",
            ),
        )
        val gardenNo = checkNotNull(garden.id)

        repeat(10) { index ->
            val memberEntity = userRepository.save(
                seedUser.copyForGardenTest(
                    userId = null,
                    email = if (index == 0) {
                        "gardeninvitecapacityleader@example.com"
                    } else {
                        "gardeninvitecapacity$index@example.com"
                    },
                    nick = if (index == 0) "capacity-leader" else "capacity$index",
                ),
            )
            gardenMemberRepository.save(
                GardenMemberEntity(
                    garden = garden,
                    user = memberEntity,
                    isLeader = index == 0,
                    isMain = index == 0,
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

        assertEquals(10L, gardenMemberRepository.countByGardenId(gardenNo))
        assertEquals(null, gardenMemberRepository.findByGardenIdAndUserId(gardenNo, joinerNo))
        verifyNoInteractions(pushService)
        verifyNoInteractions(fcmClient)
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
        userId: Long,
        title: String,
        isLeader: Boolean,
        isMain: Boolean,
        signDate: LocalDateTime = LocalDateTime.of(2026, 4, 6, 10, 0, 0),
    ): Long {
        val user = checkNotNull(userRepository.findById(userId).orElse(null))
        val garden = gardenRepository.save(
            GardenEntity(
                title = title,
                info = "$title 소개",
                color = "green",
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = isLeader,
                isMain = isMain,
                joinDate = signDate,
            ),
        )
        return garden.id
    }

    private fun createStoredImage(relativePath: String) {
        val storedPath = imagesRoot.resolve(relativePath)
        Files.createDirectories(checkNotNull(storedPath.parent))
        Files.writeString(storedPath, "image-bytes")
    }

    private fun std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity.copyForGardenTest(
        userId: Long? = this.id,
        email: String = this.email,
        password: String = this.password,
        createdAt: LocalDateTime = this.createdAt,
        nick: String = this.nick,
        image: String = this.image,
        fcm: String = this.fcm,
        socialId: String = this.socialId,
        socialType: String = this.socialType,
        authNumber: String? = this.authNumber,
    ) = std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity(
        id = userId ?: 0,
        email = email,
        password = password,
        createdAt = createdAt,
        nick = nick,
        image = image,
        fcm = fcm,
        socialId = socialId,
        socialType = socialType,
        authNumber = authNumber,
    )

    private fun removeSignupGarden(userNo: Long) {
        gardenMemberRepository.findAllByUserId(userNo).forEach { membership ->
            gardenMemberRepository.delete(membership)
            gardenRepository.deleteById(membership.garden.id)
        }
    }
}
