package std.nooook.readinggardenkotlin.modules.push

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class PushControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenMemberRepository: GardenMemberRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
) {
    @MockitoBean
    private lateinit var fcmClient: FcmClient

    @MockitoBean(name = "pushClock")
    private lateinit var pushClock: Clock

    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        gardenMemberRepository.deleteAll()
        gardenRepository.deleteAll()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `get push should read push row created during signup`() {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"push_it@example.com","user_password":"pw1234","user_fcm":"first-fcm","user_social_id":"","user_social_type":""}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("회원가입 성공"))
            .andReturn()

        val signupBody = objectMapper.readTree(signupResponse.response.contentAsString)
        val accessToken = signupBody.path("data").path("access_token").asText()
        val user = assertNotNull(userRepository.findByEmail("push_it@example.com"))
        val userNo = assertNotNull(user.id)
        val push = assertNotNull(pushSettingsRepository.findByUserId(userNo))

        assertEquals(true, push.appOk)
        assertEquals(false, push.bookOk)
        assertEquals(null, push.pushTime)

        mockMvc.perform(
            get("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 조회 성공"))
            .andExpect(jsonPath("$.data.push_app_ok").value(true))
            .andExpect(jsonPath("$.data.push_book_ok").value(false))
            .andExpect(jsonPath("$.data.push_time").doesNotExist())
    }

    @Test
    fun `update push book ok should preserve app ok and time`() {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"push_book@example.com","user_password":"pw1234","user_fcm":"first-fcm","user_social_id":"","user_social_type":""}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val accessToken = objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()

        val user = assertNotNull(userRepository.findByEmail("push_book@example.com"))
        val userNo = assertNotNull(user.id)

        mockMvc.perform(
            put("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_book_ok":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 수정 성공"))

        val push = assertNotNull(pushSettingsRepository.findByUserId(userNo))
        assertEquals(true, push.appOk)
        assertEquals(true, push.bookOk)
        assertEquals(null, push.pushTime)
    }

    @Test
    fun `update push time should persist provided timestamp`() {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"push_time@example.com","user_password":"pw1234","user_fcm":"first-fcm","user_social_id":"","user_social_type":""}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val accessToken = objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()

        val user = assertNotNull(userRepository.findByEmail("push_time@example.com"))
        val userNo = assertNotNull(user.id)
        val expectedPushTime = LocalDateTime.of(2026, 4, 6, 12, 30, 0)

        mockMvc.perform(
            put("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_time":"2026-04-06T12:30:00"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 수정 성공"))

        val push = assertNotNull(pushSettingsRepository.findByUserId(userNo))
        assertEquals(true, push.appOk)
        assertEquals(false, push.bookOk)
        assertEquals(expectedPushTime, push.pushTime)
    }

    @Test
    fun `send book push should dispatch only matching book subscribers`() {
        val fixedNow = LocalDateTime.of(2026, 4, 7, 9, 15, 0)
        val zoneId = ZoneId.of("Asia/Seoul")
        given(pushClock.instant()).willReturn(fixedNow.atZone(zoneId).toInstant())
        given(pushClock.zone).willReturn(zoneId)

        val expected = listOf(mapOf("result" to "sent", "token" to "book-valid-token"))
        given(
            fcmClient.sendToMany(
                listOf("book-valid-token"),
                "\uD83D\uDCA7물 주는 시간이에요!",
                "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
                emptyMap(),
            ),
        ).willReturn(expected)

        val accessToken = signupAndGetAccessToken("book_sender@example.com", "sender-token")
        val validUserNo = signupAndGetUserNo("book_valid@example.com", "  book-valid-token  ")
        val pushDisabledUserNo = signupAndGetUserNo("book_disabled@example.com", "book-disabled-token")
        val timeMissingUserNo = signupAndGetUserNo("book_notime@example.com", "book-no-time-token")
        val wrongTimeUserNo = signupAndGetUserNo("book_wrongtime@example.com", "book-wrong-time-token")
        val blankTokenUserNo = signupAndGetUserNo("book_blank@example.com", "   ")

        updatePushSettings(validUserNo, appOk = false, bookOk = true, pushTime = fixedNow)
        updatePushSettings(pushDisabledUserNo, appOk = true, bookOk = false, pushTime = fixedNow)
        updatePushSettings(timeMissingUserNo, appOk = true, bookOk = true, pushTime = null)
        updatePushSettings(wrongTimeUserNo, appOk = true, bookOk = true, pushTime = fixedNow.plusMinutes(1))
        updatePushSettings(blankTokenUserNo, appOk = true, bookOk = true, pushTime = fixedNow)

        mockMvc.perform(
            post("/api/v1/push/book")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 알림 푸시 전송 성공"))
            .andExpect(jsonPath("$.data[0].result").value("sent"))
            .andExpect(jsonPath("$.data[0].token").value("book-valid-token"))

        verify(fcmClient).sendToMany(
            listOf("book-valid-token"),
            "\uD83D\uDCA7물 주는 시간이에요!",
            "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
            emptyMap(),
        )
    }

    @Test
    fun `send notice push should dispatch only matching app subscribers`() {
        val expected = listOf(mapOf("result" to "sent", "token" to "notice-valid-token"))
        given(
            fcmClient.sendToMany(
                listOf("notice-valid-token"),
                "독서가든",
                "공지사항 내용",
                emptyMap(),
            ),
        ).willReturn(expected)

        val senderEmail = "notice_sender@example.com"
        val accessToken = signupAndGetAccessToken(senderEmail, "sender-token")
        val senderUserNo = assertNotNull(userRepository.findByEmail(senderEmail)?.id)
        val validUserNo = signupAndGetUserNo("notice_valid@example.com", " notice-valid-token ")
        val appDisabledUserNo = signupAndGetUserNo("notice_disabled@example.com", "notice-disabled-token")
        val blankTokenUserNo = signupAndGetUserNo("notice_blank@example.com", "   ")

        updatePushSettings(senderUserNo, appOk = false, bookOk = false, pushTime = null)
        updatePushSettings(validUserNo, appOk = true, bookOk = false, pushTime = null)
        updatePushSettings(appDisabledUserNo, appOk = false, bookOk = true, pushTime = LocalDateTime.now())
        updatePushSettings(blankTokenUserNo, appOk = true, bookOk = true, pushTime = LocalDateTime.now())

        mockMvc.perform(
            post("/api/v1/push/notice")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("content", "공지사항 내용"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("공지사항 푸시 전송 성공"))
            .andExpect(jsonPath("$.data[0].result").value("sent"))
            .andExpect(jsonPath("$.data[0].token").value("notice-valid-token"))

        verify(fcmClient).sendToMany(
            listOf("notice-valid-token"),
            "독서가든",
            "공지사항 내용",
            emptyMap(),
        )
    }

    @Test
    fun `send notice push should clear unregistered fcm token`() {
        val expected = listOf(
            mapOf(
                "result" to "failed",
                "token" to "notice-stale-token",
                "error_code" to "UNREGISTERED",
                "error" to "registration token is not registered",
            ),
        )
        given(
            fcmClient.sendToMany(
                listOf("notice-stale-token"),
                "독서가든",
                "공지사항 내용",
                emptyMap(),
            ),
        ).willReturn(expected)

        val senderEmail = "notice_cleanup_sender@example.com"
        val accessToken = signupAndGetAccessToken(senderEmail, "sender-token")
        val senderUserNo = assertNotNull(userRepository.findByEmail(senderEmail)?.id)
        val staleUserNo = signupAndGetUserNo("notice_stale@example.com", " notice-stale-token ")

        updatePushSettings(senderUserNo, appOk = false, bookOk = false, pushTime = null)
        updatePushSettings(staleUserNo, appOk = true, bookOk = false, pushTime = null)

        mockMvc.perform(
            post("/api/v1/push/notice")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("content", "공지사항 내용"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data[0].result").value("failed"))
            .andExpect(jsonPath("$.data[0].error_code").value("UNREGISTERED"))

        val staleUser = assertNotNull(userRepository.findByEmail("notice_stale@example.com"))
        assertEquals("", staleUser.fcm)
    }

    private fun signupAndGetAccessToken(
        email: String,
        fcmToken: String,
    ): String {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"pw1234","user_fcm":"$fcmToken","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()
    }

    private fun signupAndGetUserNo(
        email: String,
        fcmToken: String,
    ): Long {
        signupAndGetAccessToken(email, fcmToken)
        return assertNotNull(userRepository.findByEmail(email)?.id)
    }

    private fun updatePushSettings(
        userNo: Long,
        appOk: Boolean,
        bookOk: Boolean,
        pushTime: LocalDateTime?,
    ) {
        val push = assertNotNull(pushSettingsRepository.findByUserId(userNo))
        push.appOk = appOk
        push.bookOk = bookOk
        push.pushTime = pushTime
        pushSettingsRepository.save(push)
    }
}
