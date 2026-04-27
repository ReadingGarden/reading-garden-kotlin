package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime

@Service
class PushDeliveryService(
    private val pushSettingsRepository: PushSettingsRepository,
    private val userRepository: UserRepository,
    private val gardenRepository: GardenRepository,
    private val fcmClient: FcmClient,
    @Qualifier("pushClock")
    private val pushClock: Clock,
) {
    fun sendBookPush(): List<Map<String, Any>> {
        val now = LocalDateTime.now(pushClock)
        val allPushTargets = pushSettingsRepository.findAllByBookOkTrueAndPushTimeIsNotNull()
        val targets = allPushTargets
            .filter { push ->
                val pushTime = push.pushTime ?: return@filter false
                pushTime.hour == now.hour && pushTime.minute == now.minute
            }

        logger.debug(
            "Book push check: now={}:{}, totalPushEnabled={}, matchingTargets={}, targetUserIds={}",
            now.hour, now.minute, allPushTargets.size, targets.size, targets.map { it.user.id },
        )

        val tokens = selectTokens(targets.map { it.user.id })
        if (tokens.isEmpty()) {
            if (targets.isNotEmpty()) {
                logger.warn("Book push targets found but no FCM tokens available: userIds={}", targets.map { it.user.id })
            }
            return emptyList()
        }

        logger.info("Sending book push to {} tokens", tokens.size)
        val results = fcmClient.sendToMany(
            tokens = tokens,
            title = "💧물 주는 시간이에요!",
            body = "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
            data = HashMap(),
        )
        logAndCleanupResults("Book push", results)
        return results
    }

    fun sendNoticePush(content: String): List<Map<String, Any>> {
        val targets = pushSettingsRepository.findAllByAppOkTrue()
        val tokens = selectTokens(targets.map { it.user.id })
        if (tokens.isEmpty()) {
            return emptyList()
        }

        val results = fcmClient.sendToMany(
            tokens = tokens,
            title = "독서가든",
            body = content,
            data = HashMap(),
        )
        logAndCleanupResults("Notice push", results)
        return results
    }

    fun sendNewMemberPush(
        userId: Long,
        gardenNo: Long,
    ): List<Map<String, Any>> {
        userRepository.findById(userId).orElse(null) ?: return emptyList()
        val push = pushSettingsRepository.findByUserId(userId)
            ?.takeIf { it.appOk }
            ?: return emptyList()
        val tokens = selectTokens(listOf(push.user.id))
        if (tokens.isEmpty()) {
            return emptyList()
        }

        val gardenTitle = gardenRepository.findById(gardenNo)
            .orElse(null)
            ?.title
            ?: return emptyList()

        val results = fcmClient.sendToMany(
            tokens = tokens,
            title = "NEW 가드너 등장🧑‍🌾",
            body = "$gardenTitle" + "에 새로운 멤버가 들어왔어요. 함께 책을 읽어 가든을 채워주세요",
            data = mapOf("garden_no" to gardenNo.toString()),
        )
        logAndCleanupResults("New member push", results)
        return results
    }

    private fun selectTokens(userIds: List<Long>): List<String> {
        if (userIds.isEmpty()) {
            return emptyList()
        }

        val usersById = userRepository.findAllByIdIn(userIds)
            .associateBy { it.id }

        return userIds.mapNotNull { userId ->
            usersById[userId]?.fcm
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun logAndCleanupResults(
        pushName: String,
        results: List<Map<String, Any>>,
    ) {
        val sentCount = results.count { it["result"] == "sent" }
        val failedCount = results.count { it["result"] == "failed" }
        val staleTokens = results
            .filter { it["result"] == "failed" && it.isStaleFcmTokenFailure() }
            .mapNotNull { it["token"]?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .distinct()

        if (failedCount == 0) {
            logger.info("{} completed: sentCount={}, failedCount=0", pushName, sentCount)
        } else {
            logger.warn(
                "{} completed with failures: sentCount={}, failedCount={}, staleTokenCount={}, errorCodes={}",
                pushName,
                sentCount,
                failedCount,
                staleTokens.size,
                results.errorCodes(),
            )
        }

        if (staleTokens.isNotEmpty()) {
            val cleanedCount = userRepository.clearFcmTokens(staleTokens)
            logger.warn(
                "{} stale FCM tokens cleaned: staleTokenCount={}, cleanedUserCount={}",
                pushName,
                staleTokens.size,
                cleanedCount,
            )
        }
    }

    private fun Map<String, Any>.isStaleFcmTokenFailure(): Boolean {
        val errorCode = this["error_code"]?.toString().orEmpty()
        val error = this["error"]?.toString().orEmpty()
        return errorCode == "UNREGISTERED" ||
            errorCode == "messaging/registration-token-not-registered" ||
            (errorCode == "HTTP_404" && (error.contains("UNREGISTERED") || error.contains("NotRegistered")))
    }

    private fun List<Map<String, Any>>.errorCodes(): List<String> =
        mapNotNull { result ->
            result["error_code"]
                ?.toString()
                ?.takeIf(String::isNotBlank)
        }.distinct()

    companion object {
        private val logger = LoggerFactory.getLogger(PushDeliveryService::class.java)
    }
}
