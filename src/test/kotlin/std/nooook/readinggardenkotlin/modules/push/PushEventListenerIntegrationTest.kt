package std.nooook.readinggardenkotlin.modules.push

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import std.nooook.readinggardenkotlin.modules.push.service.GardenMemberJoinedPushEvent
import std.nooook.readinggardenkotlin.modules.push.service.PushService

@SpringBootTest
@Import(TestcontainersConfiguration::class, PushEventListenerIntegrationTest.TestConfig::class)
class PushEventListenerIntegrationTest(
    @Autowired private val transactionProbe: TransactionalEventProbe,
) {
    @MockitoSpyBean
    private lateinit var pushService: PushService

    @BeforeEach
    fun setUp() {
        clearInvocations(pushService)
    }

    @Test
    fun `garden member joined event should dispatch push after commit only`() {
        transactionProbe.publishAndCommit(
            GardenMemberJoinedPushEvent(
                gardenNo = 101,
                recipientUserIds = listOf(7, 9),
            ),
        )

        verify(pushService).sendNewMemberPush(7, 101)
        verify(pushService).sendNewMemberPush(9, 101)

        clearInvocations(pushService)

        transactionProbe.publishAndRollback(
            GardenMemberJoinedPushEvent(
                gardenNo = 202,
                recipientUserIds = listOf(11, 13),
            ),
        )

        verifyNoInteractions(pushService)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionProbe(
            transactionManager: PlatformTransactionManager,
            applicationEventPublisher: ApplicationEventPublisher,
        ): TransactionalEventProbe = TransactionalEventProbe(
            transactionTemplate = TransactionTemplate(transactionManager),
            applicationEventPublisher = applicationEventPublisher,
        )
    }
}

class TransactionalEventProbe(
    private val transactionTemplate: TransactionTemplate,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun publishAndCommit(event: GardenMemberJoinedPushEvent) {
        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(event)
        }
    }

    fun publishAndRollback(event: GardenMemberJoinedPushEvent) {
        transactionTemplate.executeWithoutResult { status ->
            applicationEventPublisher.publishEvent(event)
            status.setRollbackOnly()
        }
    }
}
