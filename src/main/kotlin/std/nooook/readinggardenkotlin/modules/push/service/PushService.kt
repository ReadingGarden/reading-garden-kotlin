package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime

@Service
class PushService(
    private val pushRepository: PushRepository,
) {
    fun getPush(userNo: Int): PushResponse {
        val push = requireNotNull(pushRepository.findByUserNo(userNo)) {
            "Push settings not found for user $userNo"
        }

        return PushResponse(
            push_app_ok = push.pushAppOk,
            push_book_ok = push.pushBookOk,
            push_time = push.pushTime,
        )
    }

    fun sendBookPush(): List<Map<String, Any>> {
        return emptyList()
    }

    fun updatePush(
        userNo: Int,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) {
        // Controller contract is wired first; persistence work happens in later slices.
    }

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
