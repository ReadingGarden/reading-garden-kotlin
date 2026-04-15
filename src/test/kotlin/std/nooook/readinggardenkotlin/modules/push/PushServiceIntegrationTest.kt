package std.nooook.readinggardenkotlin.modules.push

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class PushServiceIntegrationTest(
    @Autowired private val pushService: PushService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val gardenRepository: GardenRepository,
) {
    @MockitoBean
    private lateinit var fcmClient: FcmClient

    @BeforeEach
    fun setUp() {
        pushSettingsRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `send new member push should return empty list when app push is disabled`() {
        val savedUser = createUser("push-app-disabled@example.com", "valid-token")
        val userNo = savedUser.id
        val gardenNo = createGarden("앱푸시 비활성 가든")
        pushSettingsRepository.save(
            PushSettingsEntity(
                user = savedUser,
                appOk = false,
                bookOk = false,
            ),
        )

        val result = pushService.sendNewMemberPush(userNo, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    @Test
    fun `send new member push should return empty list when fcm token is blank`() {
        val savedUser = createUser("push-blank-token@example.com", "   ")
        val userNo = savedUser.id
        val gardenNo = createGarden("빈토큰 가든")
        pushSettingsRepository.save(
            PushSettingsEntity(
                user = savedUser,
                appOk = true,
                bookOk = false,
            ),
        )

        val result = pushService.sendNewMemberPush(userNo, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    @Test
    fun `send new member push should preserve empty list semantics when no recipient is valid`() {
        val gardenNo = createGarden("수신자 없음 가든")

        val result = pushService.sendNewMemberPush(999999L, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    private fun createUser(
        email: String,
        fcmToken: String,
    ): UserEntity =
        userRepository.save(
            UserEntity(
                email = email,
                password = "pw1234",
                nick = email.substringBefore("@"),
                image = "default.png",
                fcm = fcmToken,
                socialId = "",
                socialType = "",
            ),
        )

    private fun createGarden(title: String): Long =
        gardenRepository.save(
            GardenEntity(
                title = title,
                info = "$title 소개",
                color = "green",
            ),
        ).id
}
