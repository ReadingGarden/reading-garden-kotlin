package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import java.time.LocalDateTime

@Service
class PushService(
    private val pushPreferenceService: PushPreferenceService,
    private val pushDeliveryService: PushDeliveryService,
) {
    fun getPush(userId: Long): PushResponse = pushPreferenceService.getPush(userId)

    fun sendBookPush(): List<Map<String, Any>> = pushDeliveryService.sendBookPush()

    fun updatePush(
        userId: Long,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) = pushPreferenceService.updatePush(
        userId = userId,
        push_app_ok = push_app_ok,
        push_book_ok = push_book_ok,
        push_time = push_time,
    )

    fun sendNoticePush(content: String): List<Map<String, Any>> = pushDeliveryService.sendNoticePush(content)

    fun sendNewMemberPush(
        userId: Long,
        gardenNo: Long,
    ): List<Map<String, Any>> = pushDeliveryService.sendNewMemberPush(userId, gardenNo)
}
