package std.nooook.readinggardenkotlin.modules.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.entity.RefreshTokenEntity
import std.nooook.readinggardenkotlin.modules.auth.integration.MailSender
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.auth.service.AuthService
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
import std.nooook.readinggardenkotlin.modules.scheduler.repository.ApschedulerJobRepository
import std.nooook.readinggardenkotlin.modules.scheduler.service.AuthPasswordResetExpiryJobService
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionPhase
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecord
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecorder
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobTriggerSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.TimeZone
import kotlin.test.assertEquals
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(
    properties = [
        "app.auth.password-reset-auth-ttl=PT2S",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
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
    @Autowired private val apschedulerJobRepository: ApschedulerJobRepository,
    @Autowired private val passwordResetExpiryJobService: AuthPasswordResetExpiryJobService,
    @Autowired private val authService: AuthService,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val recordingMailSender: RecordingMailSender,
    @Autowired private val recordingTaskScheduler: RecordingTaskScheduler,
    @Autowired private val recordingSchedulerJobExecutionRecorder: RecordingSchedulerJobExecutionRecorder,
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
        apschedulerJobRepository.deleteAll()
        userRepository.deleteAll()
        recordingMailSender.sentMessages.clear()
        recordingTaskScheduler.clear()
        recordingSchedulerJobExecutionRecorder.records.clear()
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
    fun `login should delete all previous refresh tokens before issuing a new one`() {
        signup("refreshcleanup@example.com", "pw1234", "fcm-a")
        val userNo = checkNotNull(userRepository.findByUserEmail("refreshcleanup@example.com")?.userNo)

        refreshTokenRepository.save(
            RefreshTokenEntity(
                userNo = userNo,
                token = "stale-token-1",
                exp = LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            ),
        )
        refreshTokenRepository.save(
            RefreshTokenEntity(
                userNo = userNo,
                token = "stale-token-2",
                exp = LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            ),
        )

        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"refreshcleanup@example.com","user_password":"pw1234","user_fcm":"fcm-b","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andReturn()

        val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
        val newRefreshToken = loginBody.path("data").path("refresh_token").asText()
        val storedTokens = refreshTokenRepository.findAllByUserNo(userNo)

        assertEquals(1, storedTokens.size)
        assertEquals(newRefreshToken, storedTokens.single().token)
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
    fun `find password should persist auth expiry job`() {
        signup("resetpersist@example.com", "before-password", "fcm-reset-persist")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetpersist@example.com"}"""),
        )
            .andExpect(status().isOk)

        val userNo = checkNotNull(userRepository.findByUserEmail("resetpersist@example.com")?.userNo)
        val persistedJob = apschedulerJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo))

        kotlin.test.assertTrue(persistedJob.isPresent)
        kotlin.test.assertNotNull(persistedJob.get().nextRunTime)
    }

    @Test
    fun `schedule password reset expiry should register task only after outer transaction commits`() {
        signup("resetaftercommit@example.com", "before-password", "fcm-reset-after-commit")

        transactionTemplate.executeWithoutResult {
            authService.sendPasswordResetMail("resetaftercommit@example.com")

            val userNo = checkNotNull(userRepository.findByUserEmail("resetaftercommit@example.com")?.userNo)
            kotlin.test.assertTrue(
                apschedulerJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo)).isPresent,
            )
            kotlin.test.assertTrue(recordingTaskScheduler.scheduledTasks.isEmpty())
        }

        kotlin.test.assertEquals(1, recordingTaskScheduler.scheduledTasks.size)
        val scheduledTask = recordingTaskScheduler.scheduledTasks.single()
        val userNo = checkNotNull(userRepository.findByUserEmail("resetaftercommit@example.com")?.userNo)
        val persistedJob = checkNotNull(
            apschedulerJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo)).orElse(null),
        )

        kotlin.test.assertEquals(
            (persistedJob.nextRunTime!! * 1000).toLong(),
            scheduledTask.runAt.toEpochMilli(),
        )
        kotlin.test.assertTrue(recordingSchedulerJobExecutionRecorder.records.isEmpty())
    }

    @Test
    fun `expired persisted auth expiry job should fail check and clear auth number`() {
        signup("resetexpire@example.com", "before-password", "fcm-reset-expire")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetexpire@example.com"}"""),
        )
            .andExpect(status().isOk)

        val user = checkNotNull(userRepository.findByUserEmail("resetexpire@example.com"))
        val authNumber = checkNotNull(user.userAuthNumber)
        val jobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(user.userNo))
        val persistedJob = checkNotNull(apschedulerJobRepository.findById(jobId).orElse(null))
        persistedJob.nextRunTime = (System.currentTimeMillis() - 1_000).toDouble() / 1000.0
        apschedulerJobRepository.save(persistedJob)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetexpire@example.com","auth_number":"$authNumber"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("인증번호 불일치"))

        kotlin.test.assertNull(userRepository.findByUserEmail("resetexpire@example.com")?.userAuthNumber)
        kotlin.test.assertFalse(apschedulerJobRepository.findById(jobId).isPresent)
    }

    @Test
    fun `startup rehydration should clear overdue jobs and reschedule future jobs`() {
        signup("rehydrateoverdue@example.com", "before-password", "fcm-rehydrate-overdue")
        signup("rehydratefuture@example.com", "before-password", "fcm-rehydrate-future")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"rehydrateoverdue@example.com"}"""),
        )
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"rehydratefuture@example.com"}"""),
        )
            .andExpect(status().isOk)

        val overdueUser = checkNotNull(userRepository.findByUserEmail("rehydrateoverdue@example.com"))
        val futureUser = checkNotNull(userRepository.findByUserEmail("rehydratefuture@example.com"))
        val overdueJobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(overdueUser.userNo))
        val futureJobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(futureUser.userNo))
        val overdueJob = checkNotNull(apschedulerJobRepository.findById(overdueJobId).orElse(null))
        overdueJob.nextRunTime = (System.currentTimeMillis() - 1_000).toDouble() / 1000.0
        apschedulerJobRepository.save(overdueJob)

        recordingTaskScheduler.clear()
        passwordResetExpiryJobService.rehydratePasswordResetExpiryJobs()

        kotlin.test.assertNull(userRepository.findByUserEmail("rehydrateoverdue@example.com")?.userAuthNumber)
        kotlin.test.assertFalse(apschedulerJobRepository.findById(overdueJobId).isPresent)
        kotlin.test.assertTrue(apschedulerJobRepository.findById(futureJobId).isPresent)
        kotlin.test.assertEquals(1, recordingTaskScheduler.scheduledTasks.size)
        kotlin.test.assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recordingSchedulerJobExecutionRecorder.records.map { it.phase },
        )
        kotlin.test.assertEquals(
            listOf(
                SchedulerJobTriggerSource.REHYDRATED,
                SchedulerJobTriggerSource.REHYDRATED,
            ),
            recordingSchedulerJobExecutionRecorder.records.map { it.triggerSource },
        )
        kotlin.test.assertEquals(
            listOf(overdueJobId, overdueJobId),
            recordingSchedulerJobExecutionRecorder.records.map { it.jobId },
        )
        kotlin.test.assertEquals(
            "expire_overdue",
            recordingSchedulerJobExecutionRecorder.records.first().context["rehydrate_action"],
        )
        kotlin.test.assertTrue(
            recordingTaskScheduler.scheduledTasks.any { scheduledTask ->
                scheduledTask.runAt.toEpochMilli() == (
                    checkNotNull(apschedulerJobRepository.findById(futureJobId).orElse(null)).nextRunTime!! * 1000
                    ).toLong()
            },
        )

        Thread.sleep(2_500)

        kotlin.test.assertNull(userRepository.findByUserEmail("rehydratefuture@example.com")?.userAuthNumber)
        kotlin.test.assertFalse(apschedulerJobRepository.findById(futureJobId).isPresent)
    }

    @Test
    fun `reissuing password reset should keep latest auth number valid until latest ttl`() {
        signup("reissue@example.com", "before-password", "fcm-reissue")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com"}"""),
        )
            .andExpect(status().isOk)

        val firstAuthNumber = checkNotNull(userRepository.findByUserEmail("reissue@example.com")?.userAuthNumber)

        Thread.sleep(1_400)

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com"}"""),
        )
            .andExpect(status().isOk)

        val secondAuthNumber = checkNotNull(userRepository.findByUserEmail("reissue@example.com")?.userAuthNumber)
        kotlin.test.assertNotEquals(firstAuthNumber, secondAuthNumber)

        Thread.sleep(900)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com","auth_number":"$secondAuthNumber"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("인증 성공"))

        Thread.sleep(1_300)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com","auth_number":"$secondAuthNumber"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("인증번호 불일치"))
    }

    @Test
    fun `startup rehydration should delete malformed persisted jobs safely`() {
        apschedulerJobRepository.save(
            std.nooook.readinggardenkotlin.modules.scheduler.entity.ApschedulerJobEntity(
                id = "auth:password-reset-expiry:999",
                nextRunTime = Instant.now().plusSeconds(120).toEpochMilli() / 1000.0,
                jobState = "malformed".toByteArray(),
            ),
        )

        passwordResetExpiryJobService.rehydratePasswordResetExpiryJobs()

        kotlin.test.assertFalse(apschedulerJobRepository.findById("auth:password-reset-expiry:999").isPresent)
        kotlin.test.assertTrue(recordingSchedulerJobExecutionRecorder.records.isEmpty())
    }

    @Test
    fun `update profile should change nickname for app client request`() {
        val signupBody = signup("profilenick@example.com", "pw1234", "fcm-profile")
        val accessToken = signupBody.path("data").path("access_token").asText()

        mockMvc.perform(
            put("/api/v1/auth/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_nick":"변경닉네임"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("프로필 변경 성공"))
            .andExpect(jsonPath("$.data.user_email").value("profilenick@example.com"))
            .andExpect(jsonPath("$.data.user_nick").value("변경닉네임"))
            .andExpect(jsonPath("$.data.user_image").value("데이지"))

        val savedUser = checkNotNull(userRepository.findByUserEmail("profilenick@example.com"))
        kotlin.test.assertEquals("변경닉네임", savedUser.userNick)
        kotlin.test.assertEquals("데이지", savedUser.userImage)
    }

    @Test
    fun `update profile should change image for app client request`() {
        val signupBody = signup("profileimage@example.com", "pw1234", "fcm-profile-image")
        val accessToken = signupBody.path("data").path("access_token").asText()
        val beforeUser = checkNotNull(userRepository.findByUserEmail("profileimage@example.com"))
        val beforeNick = beforeUser.userNick

        mockMvc.perform(
            put("/api/v1/auth/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_image":"튤립"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("프로필 변경 성공"))
            .andExpect(jsonPath("$.data.user_email").value("profileimage@example.com"))
            .andExpect(jsonPath("$.data.user_nick").value(beforeNick))
            .andExpect(jsonPath("$.data.user_image").value("튤립"))

        val savedUser = checkNotNull(userRepository.findByUserEmail("profileimage@example.com"))
        kotlin.test.assertEquals(beforeNick, savedUser.userNick)
        kotlin.test.assertEquals("튤립", savedUser.userImage)
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

    class RecordingTaskScheduler : TaskScheduler, DisposableBean {
        private val delegate = ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("test-auth-scheduler-")
            initialize()
        }

        val scheduledTasks = CopyOnWriteArrayList<ScheduledTask>()
        private val scheduledFutures = CopyOnWriteArrayList<ScheduledFuture<*>>()

        override fun getClock() = delegate.clock

        override fun schedule(
            task: Runnable,
            startTime: Instant,
        ): ScheduledFuture<*> {
            scheduledTasks += ScheduledTask(startTime)
            val future = requireNotNull(delegate.schedule(task, startTime))
            scheduledFutures += future
            return future
        }

        override fun schedule(
            task: Runnable,
            trigger: Trigger,
        ): ScheduledFuture<*> = requireNotNull(delegate.schedule(task, trigger))

        override fun scheduleAtFixedRate(
            task: Runnable,
            startTime: Instant,
            period: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleAtFixedRate(task, startTime, period))

        override fun scheduleAtFixedRate(
            task: Runnable,
            period: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleAtFixedRate(task, period))

        override fun scheduleWithFixedDelay(
            task: Runnable,
            startTime: Instant,
            delay: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleWithFixedDelay(task, startTime, delay))

        override fun scheduleWithFixedDelay(
            task: Runnable,
            delay: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleWithFixedDelay(task, delay))

        fun clear() {
            scheduledFutures.forEach { it.cancel(false) }
            scheduledFutures.clear()
            scheduledTasks.clear()
        }

        override fun destroy() {
            clear()
            delegate.shutdown()
        }
    }

    data class ScheduledTask(
        val runAt: Instant,
    )

    class RecordingSchedulerJobExecutionRecorder : SchedulerJobExecutionRecorder {
        val records = mutableListOf<SchedulerJobExecutionRecord>()

        override fun record(record: SchedulerJobExecutionRecord) {
            records += record
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun mailSender(): RecordingMailSender = RecordingMailSender()

        @Bean
        @Primary
        fun taskScheduler(): RecordingTaskScheduler = RecordingTaskScheduler()

        @Bean
        @Primary
        fun schedulerJobExecutionRecorder(): RecordingSchedulerJobExecutionRecorder =
            RecordingSchedulerJobExecutionRecorder()
    }
}
