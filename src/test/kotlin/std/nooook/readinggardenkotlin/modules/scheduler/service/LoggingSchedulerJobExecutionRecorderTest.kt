package std.nooook.readinggardenkotlin.modules.scheduler.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant

class LoggingSchedulerJobExecutionRecorderTest {
    private val logger = LoggerFactory.getLogger(LoggingSchedulerJobExecutionRecorder::class.java) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()
    private val recorder = LoggingSchedulerJobExecutionRecorder()

    init {
        listAppender.start()
        logger.addAppender(listAppender)
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `record should log started succeeded and skipped at debug level`() {
        val occurredAt = Instant.parse("2026-04-19T04:30:00Z")

        recorder.record(
            SchedulerJobExecutionRecord.started(
                jobName = "book-push",
                jobId = "book-push:cron",
                triggerSource = SchedulerJobTriggerSource.SCHEDULED,
                startedAt = occurredAt,
                context = mapOf("job_type" to "book_push"),
            ),
        )
        recorder.record(
            SchedulerJobExecutionRecord.succeeded(
                jobName = "book-push",
                jobId = "book-push:cron",
                triggerSource = SchedulerJobTriggerSource.SCHEDULED,
                startedAt = occurredAt,
                finishedAt = occurredAt,
                context = mapOf("job_type" to "book_push"),
            ),
        )
        recorder.record(
            SchedulerJobExecutionRecord.skipped(
                jobName = "book-push",
                jobId = "book-push:cron",
                triggerSource = SchedulerJobTriggerSource.SCHEDULED,
                occurredAt = occurredAt,
                reason = "overlap",
                context = mapOf("job_type" to "book_push"),
            ),
        )

        assertEquals(
            listOf(Level.DEBUG, Level.DEBUG, Level.DEBUG),
            listAppender.list.map { it.level },
        )
    }

    @Test
    fun `record should log failed at warn level`() {
        val occurredAt = Instant.parse("2026-04-19T04:30:00Z")

        recorder.record(
            SchedulerJobExecutionRecord.failed(
                jobName = "book-push",
                jobId = "book-push:cron",
                triggerSource = SchedulerJobTriggerSource.SCHEDULED,
                startedAt = occurredAt,
                finishedAt = occurredAt,
                failure = IllegalStateException("boom"),
                context = mapOf("job_type" to "book_push"),
            ),
        )

        assertEquals(listOf(Level.WARN), listAppender.list.map { it.level })
    }
}
