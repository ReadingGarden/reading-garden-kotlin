package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository

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

    fun sendBookPush() {
        // Minimum implementation for the first migration slice.
    }

    fun sendNewMemberPush(
        userNo: Int,
        gardenNo: Int,
    ) {
        // Garden membership flow only needs a stable integration point for now.
    }
}
