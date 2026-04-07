package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import java.time.LocalDateTime

@Service
class PushService(
    private val pushPreferenceService: PushPreferenceService,
) {
    fun getPush(userNo: Int): PushResponse = pushPreferenceService.getPush(userNo)

    fun sendBookPush(): List<Map<String, Any>> {
        return emptyList()
    }

    fun updatePush(
        userNo: Int,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) = pushPreferenceService.updatePush(
        userNo = userNo,
        push_app_ok = push_app_ok,
        push_book_ok = push_book_ok,
        push_time = push_time,
    )

    fun sendNoticePush(content: String): List<Map<String, Any>> {
        return emptyList()
    }

    fun sendNewMemberPush(
        userNo: Int,
        gardenNo: Int,
    ) {
        // Garden membership flow only needs a stable integration point for now.
    }
}
