package std.nooook.readinggardenkotlin.modules.push

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    @Autowired private val gardenUserRepository: GardenUserRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val pushRepository: PushRepository,
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
        val user = assertNotNull(userRepository.findByUserEmail("push_it@example.com"))
        val userNo = assertNotNull(user.userNo)
        val push = assertNotNull(pushRepository.findByUserNo(userNo))

        assertEquals(true, push.pushAppOk)
        assertEquals(false, push.pushBookOk)
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

        val user = assertNotNull(userRepository.findByUserEmail("push_book@example.com"))
        val userNo = assertNotNull(user.userNo)

        mockMvc.perform(
            put("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_book_ok":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("푸시 알림 수정 성공"))

        val push = assertNotNull(pushRepository.findByUserNo(userNo))
        assertEquals(true, push.pushAppOk)
        assertEquals(true, push.pushBookOk)
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

        val user = assertNotNull(userRepository.findByUserEmail("push_time@example.com"))
        val userNo = assertNotNull(user.userNo)
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

        val push = assertNotNull(pushRepository.findByUserNo(userNo))
        assertEquals(true, push.pushAppOk)
        assertEquals(false, push.pushBookOk)
        assertEquals(expectedPushTime, push.pushTime)
    }
}
