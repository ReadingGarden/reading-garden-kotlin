package std.nooook.readinggardenkotlin.modules.garden

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenUserRepository
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
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
    @Autowired private val gardenService: GardenService,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        pushRepository.deleteAll()
        gardenUserRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
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
}
