package std.nooook.readinggardenkotlin.modules.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.integration.MailSender
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthControllerIntegrationTest.TestConfig::class)
class AuthControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val recordingMailSender: RecordingMailSender,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookRepository.deleteAll()
        pushRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        userRepository.deleteAll()
        recordingMailSender.sentMessages.clear()
    }

    @Test
    fun `signup should create default garden push and refresh token`() {
        val response = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"new@example.com","user_password":"pw1234","user_fcm":"first-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("회원가입 성공"))
            .andExpect(jsonPath("$.data.access_token").isString)
            .andExpect(jsonPath("$.data.refresh_token").isString)
            .andReturn()

        val body = objectMapper.readTree(response.response.contentAsString)
        val user = userRepository.findByUserEmail("new@example.com")
        checkNotNull(user)
        val userNo = checkNotNull(user.userNo)

        kotlin.test.assertNotEquals("pw1234", user.userPassword)
        kotlin.test.assertEquals("first-fcm", user.userFcm)
        kotlin.test.assertEquals(1L, gardenUserRepository.countByUserNo(userNo))
        kotlin.test.assertNotNull(pushRepository.findByUserNo(userNo))
        kotlin.test.assertEquals(
            body.path("data").path("refresh_token").asText(),
            refreshTokenRepository.findByUserNo(userNo)?.token,
        )
    }

    @Test
    fun `duplicate email signup should return legacy conflict envelope`() {
        signup("dup@example.com", "pw", "fcm-1")

        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"dup@example.com","user_password":"pw","user_fcm":"fcm-2","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resp_code").value(409))
            .andExpect(jsonPath("$.resp_msg").value("이메일 중복"))
    }

    @Test
    fun `signup should reject invalid email format with legacy validation envelope`() {
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"invalid-email","user_password":"pw","user_fcm":"fcm-2","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request body validation failed."))
            .andExpect(jsonPath("$.errors[0].field").value("user_email"))

        kotlin.test.assertNull(userRepository.findByUserEmail("invalid-email"))
    }

    @Test
    fun `login profile refresh and logout should preserve legacy auth flow`() {
        signup("flow@example.com", "pw1234", "fcm-1")

        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"flow@example.com","user_password":"pw1234","user_fcm":"updated-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("로그인 성공"))
            .andExpect(jsonPath("$.data.access_token").isString)
            .andExpect(jsonPath("$.data.refresh_token").isString)
            .andReturn()

        val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
        val accessToken = loginBody.path("data").path("access_token").asText()
        val refreshToken = loginBody.path("data").path("refresh_token").asText()
        val user = userRepository.findByUserEmail("flow@example.com")
        checkNotNull(user)

        kotlin.test.assertEquals("updated-fcm", user.userFcm)

        mockMvc.perform(
            get("/api/v1/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("조회 성공"))
            .andExpect(jsonPath("$.data.user_email").value("flow@example.com"))
            .andExpect(jsonPath("$.data.garden_count").value(1))

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"$refreshToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("토큰 발급 성공"))
            .andExpect(jsonPath("$.data").isString)

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("로그아웃 성공"))

        kotlin.test.assertEquals("", userRepository.findByUserEmail("flow@example.com")?.userFcm)
        kotlin.test.assertNull(refreshTokenRepository.findByUserNo(checkNotNull(user.userNo)))

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"$refreshToken"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `refresh should accept expiry stored in utc local time`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            signup("tz@example.com", "pw1234", "fcm-tz")

            val loginResponse = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"user_email":"tz@example.com","user_password":"pw1234","user_fcm":"fcm-tz-2","user_social_id":"","user_social_type":""}""",
                    ),
            )
                .andExpect(status().isOk)
                .andReturn()

            val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
            val refreshToken = loginBody.path("data").path("refresh_token").asText()
            val userNo = checkNotNull(userRepository.findByUserEmail("tz@example.com")?.userNo)
            val storedToken = checkNotNull(refreshTokenRepository.findByUserNo(userNo))
            storedToken.exp = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)
            refreshTokenRepository.save(storedToken)

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refresh_token":"$refreshToken"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.resp_code").value(200))
                .andExpect(jsonPath("$.resp_msg").value("토큰 발급 성공"))
                .andExpect(jsonPath("$.data").isString)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `find password flow should send mail verify auth code and update password`() {
        signup("reset@example.com", "before-password", "fcm-reset")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메일이 발송되었습니다. 확인해주세요."))

        kotlin.test.assertEquals(1, recordingMailSender.sentMessages.size)
        val sentMessage = recordingMailSender.sentMessages.single()
        kotlin.test.assertEquals("reset@example.com", sentMessage.email)
        kotlin.test.assertEquals("[독서가든] 인증번호 안내드립니다", sentMessage.title)

        val authNumber = userRepository.findByUserEmail("reset@example.com")?.userAuthNumber
        checkNotNull(authNumber)
        kotlin.test.assertEquals(authNumber, sentMessage.content)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com","auth_number":"$authNumber"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("인증 성공"))

        mockMvc.perform(
            put("/api/v1/auth/find-password/update-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com","user_password":"after-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("비밀번호 변경 성공"))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"reset@example.com","user_password":"after-password","user_fcm":"new-reset-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_msg").value("로그인 성공"))
    }

    @Test
    fun `delete user should remove auth garden book and memo data`() {
        val signupBody = signup("delete@example.com", "pw1234", "fcm-delete")
        val accessToken = signupBody.path("data").path("access_token").asText()
        val user = userRepository.findByUserEmail("delete@example.com")
        checkNotNull(user)
        val userNo = checkNotNull(user.userNo)
        val gardenNo = checkNotNull(gardenUserRepository.findAllByUserNo(userNo).singleOrNull()?.gardenNo)

        val book = bookRepository.save(
            BookEntity(
                userNo = userNo,
                bookTitle = "테스트 책",
                bookAuthor = "작가",
                bookPublisher = "출판사",
                bookStatus = 1,
                bookPage = 100,
                bookInfo = "소개",
            ),
        )
        val bookNo = checkNotNull(book.bookNo)
        bookReadRepository.save(BookReadEntity(bookNo = bookNo, userNo = userNo))
        bookImageRepository.save(BookImageEntity(bookNo = bookNo, imageName = "img", imageUrl = "book.png"))

        val memo = memoRepository.save(
            MemoEntity(
                bookNo = bookNo,
                memoContent = "메모",
                userNo = userNo,
            ),
        )
        val memoNo = checkNotNull(memo.id)
        memoImageRepository.save(MemoImageEntity(memoNo = memoNo, imageName = "memo", imageUrl = "memo.png"))

        mockMvc.perform(
            delete("/api/v1/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("회원 탈퇴 성공"))

        kotlin.test.assertNull(userRepository.findByUserNo(userNo))
        kotlin.test.assertNull(refreshTokenRepository.findByUserNo(userNo))
        kotlin.test.assertNull(pushRepository.findByUserNo(userNo))
        kotlin.test.assertTrue(bookRepository.findAllByUserNo(userNo).isEmpty())
        kotlin.test.assertTrue(memoRepository.findAllByUserNo(userNo).isEmpty())
        kotlin.test.assertEquals(0L, gardenUserRepository.countByUserNo(userNo))
        kotlin.test.assertTrue(gardenRepository.findById(gardenNo).isPresent)
    }

    private fun signup(
        email: String,
        password: String,
        fcmToken: String,
    ) = objectMapper.readTree(
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"$password","user_fcm":"$fcmToken","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString,
    )

    class RecordingMailSender : MailSender {
        val sentMessages = mutableListOf<SentMessage>()

        override fun send(
            email: String,
            title: String,
            content: String,
        ) {
            sentMessages += SentMessage(email, title, content)
        }
    }

    data class SentMessage(
        val email: String,
        val title: String,
        val content: String,
    )

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun mailSender(): RecordingMailSender = RecordingMailSender()
    }
}
