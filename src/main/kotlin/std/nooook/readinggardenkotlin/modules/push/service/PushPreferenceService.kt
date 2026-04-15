package std.nooook.readinggardenkotlin.modules.push.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.time.LocalDateTime

@Service
class PushPreferenceService(
    private val pushSettingsRepository: PushSettingsRepository,
) {
    fun getPush(userId: Long): PushResponse {
        val push = requireNotNull(pushSettingsRepository.findByUserId(userId)) {
            "Push settings not found for user $userId"
        }

        return PushResponse(
            user_no = push.user.id,
            push_app_ok = push.appOk,
            push_book_ok = push.bookOk,
            push_time = push.pushTime,
        )
    }

    @Transactional
    fun updatePush(
        userId: Long,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) {
        val push = requireNotNull(pushSettingsRepository.findByUserId(userId)) {
            "Push settings not found for user $userId"
        }

        if (push_app_ok != null) {
            push.appOk = push_app_ok
        }
        if (push_book_ok != null) {
            push.bookOk = push_book_ok
        }
        if (push_time != null) {
            push.pushTime = push_time
        }

        pushSettingsRepository.save(push)
    }
}
