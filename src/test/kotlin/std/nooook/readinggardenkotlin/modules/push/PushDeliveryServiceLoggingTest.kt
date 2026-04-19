package std.nooook.readinggardenkotlin.modules.push

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushDeliveryService
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class PushDeliveryServiceLoggingTest {
    private val logger = LoggerFactory.getLogger(PushDeliveryService::class.java) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()
    private val pushSettingsRepository: PushSettingsRepository = mock(PushSettingsRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val gardenRepository: GardenRepository = mock(GardenRepository::class.java)
    private val fcmClient: FcmClient = mock(FcmClient::class.java)
    private val service = PushDeliveryService(
        pushSettingsRepository = pushSettingsRepository,
        userRepository = userRepository,
        gardenRepository = gardenRepository,
        fcmClient = fcmClient,
        pushClock = Clock.fixed(Instant.parse("2026-04-19T04:24:00Z"), ZoneId.of("Asia/Seoul")),
    )

    init {
        listAppender.start()
        logger.addAppender(listAppender)
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `send book push should log no target checks at debug level`() {
        val user = UserEntity(id = 7L, fcm = "token-7")
        given(pushSettingsRepository.findAllByBookOkTrueAndPushTimeIsNotNull()).willReturn(
            listOf(
                PushSettingsEntity(
                    user = user,
                    appOk = true,
                    bookOk = true,
                    pushTime = LocalDateTime.of(2026, 4, 19, 13, 25, 0),
                ),
            ),
        )

        val result = service.sendBookPush()

        assertEquals(emptyList<Map<String, Any>>(), result)
        assertTrue(listAppender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("Book push check") })
        assertTrue(listAppender.list.none { it.level == Level.INFO && it.formattedMessage.contains("Book push check") })
    }

    @Test
    fun `send book push should keep match summary out of info logs even when sending`() {
        val user = UserEntity(id = 7L, fcm = "token-7")
        given(pushSettingsRepository.findAllByBookOkTrueAndPushTimeIsNotNull()).willReturn(
            listOf(
                PushSettingsEntity(
                    user = user,
                    appOk = true,
                    bookOk = true,
                    pushTime = LocalDateTime.of(2026, 4, 19, 13, 24, 0),
                ),
            ),
        )
        given(userRepository.findAllByIdIn(listOf(7L))).willReturn(listOf(user))
        given(
            fcmClient.sendToMany(
                listOf("token-7"),
                "💧물 주는 시간이에요!",
                "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
                emptyMap(),
            ),
        ).willReturn(
            listOf(
                mapOf(
                    "result" to "sent",
                    "token" to "token-7",
                    "message_id" to UUID.randomUUID().toString(),
                ),
            ),
        )

        service.sendBookPush()

        assertTrue(listAppender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("Book push check") })
        assertTrue(listAppender.list.none { it.level == Level.INFO && it.formattedMessage.contains("Book push check") })
    }
}
