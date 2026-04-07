package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PushEventListener(
    private val pushService: PushService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGardenMemberJoined(event: GardenMemberJoinedPushEvent) {
        event.recipientUserNos.forEach { userNo ->
            pushService.sendNewMemberPush(userNo, event.gardenNo)
        }
    }
}
