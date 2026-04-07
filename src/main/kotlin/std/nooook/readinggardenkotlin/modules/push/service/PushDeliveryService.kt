package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.Clock
import java.time.LocalDateTime

@Service
class PushDeliveryService(
    private val pushRepository: PushRepository,
    private val userRepository: UserRepository,
    private val fcmClient: FcmClient,
    @Qualifier("pushClock")
    private val pushClock: Clock,
) {
    fun sendBookPush(): List<Map<String, Any>> {
        val now = LocalDateTime.now(pushClock)
        val targets = pushRepository.findAllByPushBookOkTrueAndPushTimeIsNotNull()
            .filter { push ->
                val pushTime = push.pushTime ?: return@filter false
                pushTime.hour == now.hour && pushTime.minute == now.minute
            }

        val tokens = selectTokens(targets.map { it.userNo })
        if (tokens.isEmpty()) {
            return emptyList()
        }

        return fcmClient.sendToMany(
            tokens = tokens,
            title = "💧물 주는 시간이에요!",
            body = "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
            data = emptyMap(),
        )
    }

    fun sendNoticePush(content: String): List<Map<String, Any>> {
        val targets = pushRepository.findAllByPushAppOkTrue()
        val tokens = selectTokens(targets.map { it.userNo })
        if (tokens.isEmpty()) {
            return emptyList()
        }

        return fcmClient.sendToMany(
            tokens = tokens,
            title = "독서가든",
            body = content,
            data = emptyMap(),
        )
    }

    private fun selectTokens(userNos: List<Int>): List<String> {
        if (userNos.isEmpty()) {
            return emptyList()
        }

        val usersByNo = userRepository.findAllByUserNoIn(userNos)
            .associateBy { it.userNo }

        return userNos.mapNotNull { userNo ->
            usersByNo[userNo]?.userFcm
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }
}
