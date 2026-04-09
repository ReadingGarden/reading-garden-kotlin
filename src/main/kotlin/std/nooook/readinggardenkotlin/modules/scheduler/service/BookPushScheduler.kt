package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.push.service.PushService

@Service
class BookPushScheduler(
    private val pushService: PushService,
    private val schedulerJobExecutionRunner: SchedulerJobExecutionRunner,
) {
    @Scheduled(cron = "\${app.push.book-cron:0 * * * * *}")
    fun sendBookPush() {
        schedulerJobExecutionRunner.run(
            jobName = BOOK_PUSH_JOB_NAME,
            jobId = BOOK_PUSH_JOB_ID,
            overlapKey = BOOK_PUSH_OVERLAP_KEY,
            context = mapOf(
                "job_type" to "book_push",
                "trigger_source" to "scheduled_cron",
            ),
        ) {
            pushService.sendBookPush()
        }
    }

    companion object {
        private const val BOOK_PUSH_JOB_NAME = "book-push"
        private const val BOOK_PUSH_JOB_ID = "book-push:cron"
        private const val BOOK_PUSH_OVERLAP_KEY = "book-push"
    }
}
