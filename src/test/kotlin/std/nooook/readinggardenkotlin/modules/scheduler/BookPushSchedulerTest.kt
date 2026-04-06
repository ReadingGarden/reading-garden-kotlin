package std.nooook.readinggardenkotlin.modules.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.scheduling.annotation.Scheduled
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import std.nooook.readinggardenkotlin.modules.scheduler.service.BookPushScheduler

class BookPushSchedulerTest {
    private val pushService: PushService = mock(PushService::class.java)
    private val scheduler = BookPushScheduler(pushService)

    @Test
    fun `scheduled method should keep cron contract`() {
        val method = BookPushScheduler::class.java.getDeclaredMethod("sendBookPush")
        val scheduled = method.getAnnotation(Scheduled::class.java)

        assertEquals("\${app.push.book-cron:0 * * * * *}", scheduled.cron)
    }

    @Test
    fun `scheduled method should delegate to push service`() {
        scheduler.sendBookPush()

        verify(pushService).sendBookPush()
    }
}
