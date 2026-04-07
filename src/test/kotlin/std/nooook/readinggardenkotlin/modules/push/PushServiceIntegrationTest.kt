package std.nooook.readinggardenkotlin.modules.push

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushEntity
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import kotlin.test.assertEquals

@SpringBootTest
class PushServiceIntegrationTest(
    @Autowired private val pushService: PushService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val pushRepository: PushRepository,
    @Autowired private val gardenRepository: GardenRepository,
) {
    @MockitoBean
    private lateinit var fcmClient: FcmClient

    @BeforeEach
    fun setUp() {
        pushRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `send new member push should return empty list when app push is disabled`() {
        val userNo = createUser("push-app-disabled@example.com", "valid-token")
        val gardenNo = createGarden("앱푸시 비활성 가든")
        pushRepository.save(
            PushEntity(
                userNo = userNo,
                pushAppOk = false,
                pushBookOk = false,
            ),
        )

        val result = pushService.sendNewMemberPush(userNo, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    @Test
    fun `send new member push should return empty list when fcm token is blank`() {
        val userNo = createUser("push-blank-token@example.com", "   ")
        val gardenNo = createGarden("빈토큰 가든")
        pushRepository.save(
            PushEntity(
                userNo = userNo,
                pushAppOk = true,
                pushBookOk = false,
            ),
        )

        val result = pushService.sendNewMemberPush(userNo, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    @Test
    fun `send new member push should preserve empty list semantics when no recipient is valid`() {
        val gardenNo = createGarden("수신자 없음 가든")

        val result = pushService.sendNewMemberPush(999999, gardenNo)

        assertEquals(emptyList(), result)
        verifyNoInteractions(fcmClient)
    }

    private fun createUser(
        email: String,
        fcmToken: String,
    ): Int = checkNotNull(
        userRepository.save(
            UserEntity(
                userEmail = email,
                userPassword = "pw1234",
                userNick = email.substringBefore("@"),
                userImage = "default.png",
                userFcm = fcmToken,
                userSocialId = "",
                userSocialType = "",
            ),
        ).userNo,
    )

    private fun createGarden(title: String): Int = checkNotNull(
        gardenRepository.save(
            GardenEntity(
                gardenTitle = title,
                gardenInfo = "$title 소개",
                gardenColor = "green",
            ),
        ).gardenNo,
    )
}
