package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.service.PushService

@Service
class BookPushScheduler(
    private val pushService: PushService,
) {
    @Scheduled(cron = "\${app.push.book-cron:0 * * * * *}")
    fun sendBookPush() {
        pushService.sendBookPush()
    }
}
