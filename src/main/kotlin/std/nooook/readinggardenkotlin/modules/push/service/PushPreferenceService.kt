package std.nooook.readinggardenkotlin.modules.push.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.repository.PushRepository
import java.time.LocalDateTime

@Service
class PushPreferenceService(
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

    @Transactional
    fun updatePush(
        userNo: Int,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) {
        val push = requireNotNull(pushRepository.findByUserNo(userNo)) {
            "Push settings not found for user $userNo"
        }

        if (push_app_ok != null) {
            push.pushAppOk = push_app_ok
        }
        if (push_book_ok != null) {
            push.pushBookOk = push_book_ok
        }
        if (push_time != null) {
            push.pushTime = push_time
        }

        pushRepository.save(push)
    }
}
