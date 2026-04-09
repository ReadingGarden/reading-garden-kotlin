package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

class SchedulerJobExecutionRunnerTest {
    private val fixedInstant = Instant.parse("2026-04-09T12:34:56Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `run records start and success when block completes`() {
        val recorder = RecordingSchedulerJobExecutionRecorder()
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)

        var executed = false

        runner.run("book-push") {
            executed = true
        }

        assertTrue(executed)
        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recorder.records.map { it.phase },
        )
        assertEquals(listOf("book-push", "book-push"), recorder.records.map { it.jobId })
        assertEquals(
            listOf(SchedulerJobTriggerSource.SCHEDULED, SchedulerJobTriggerSource.SCHEDULED),
            recorder.records.map { it.triggerSource },
        )
        assertEquals(fixedInstant, recorder.records[0].occurredAt)
        assertEquals(fixedInstant, recorder.records[1].occurredAt)
        assertFalse(guard.isLocked("book-push"))
    }

    @Test
    fun `run records skip when another lease is active`() {
        val recorder = RecordingSchedulerJobExecutionRecorder()
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)
        val lease = guard.tryLock("book-push")

        requireNotNull(lease)

        var executed = false

        runner.run("book-push") {
            executed = true
        }

        assertFalse(executed)
        assertEquals(listOf(SchedulerJobExecutionPhase.SKIPPED), recorder.records.map { it.phase })
        assertEquals(listOf("book-push"), recorder.records.map { it.jobId })
        assertEquals(listOf(SchedulerJobTriggerSource.SCHEDULED), recorder.records.map { it.triggerSource })
        assertEquals("overlap", recorder.records.single().reason)
        assertTrue(guard.isLocked("book-push"))

        lease.close()
        assertFalse(guard.isLocked("book-push"))
    }

    @Test
    fun `run can use a distinct overlap key from job name`() {
        val recorder = RecordingSchedulerJobExecutionRecorder()
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)
        val lease = guard.tryLock("auth:password-reset-expiry:1")

        requireNotNull(lease)

        var executed = false

        runner.run(
            jobName = "auth-password-reset-expiry",
            jobId = "auth:password-reset-expiry:2",
            overlapKey = "auth:password-reset-expiry:2",
            triggerSource = SchedulerJobTriggerSource.REHYDRATED,
        ) {
            executed = true
        }

        assertTrue(executed)
        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recorder.records.map { it.phase },
        )
        assertEquals(
            listOf(
                "auth:password-reset-expiry:2",
                "auth:password-reset-expiry:2",
            ),
            recorder.records.map { it.jobId },
        )
        assertFalse(guard.isLocked("auth:password-reset-expiry:2"))

        lease.close()
        assertFalse(guard.isLocked("auth:password-reset-expiry:1"))
    }

    @Test
    fun `run records failure and releases lease when block throws`() {
        val recorder = RecordingSchedulerJobExecutionRecorder()
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)

        val thrown = assertFailsWith<IllegalStateException> {
            runner.run("book-push") {
                throw IllegalStateException("boom")
            }
        }

        assertEquals("boom", thrown.message)
        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.FAILED,
            ),
            recorder.records.map { it.phase },
        )
        assertEquals(listOf("book-push", "book-push"), recorder.records.map { it.jobId })
        assertEquals(
            listOf(SchedulerJobTriggerSource.SCHEDULED, SchedulerJobTriggerSource.SCHEDULED),
            recorder.records.map { it.triggerSource },
        )
        assertEquals("boom", recorder.records.last().failure?.message)
        assertFalse(guard.isLocked("book-push"))
    }

    @Test
    fun `run ignores recorder failures and still releases lease`() {
        val recorder = ThrowingSchedulerJobExecutionRecorder()
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)

        runner.run(
            jobName = "book-push",
            jobId = "auth:password-reset-expiry:1",
            triggerSource = SchedulerJobTriggerSource.REHYDRATED,
        ) {
            assertTrue(guard.isLocked("book-push"))
        }

        assertFalse(guard.isLocked("book-push"))
        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recorder.seenPhases,
        )
        assertEquals(
            listOf(
                "auth:password-reset-expiry:1",
                "auth:password-reset-expiry:1",
            ),
            recorder.seenJobIds,
        )
        assertEquals(
            listOf(
                SchedulerJobTriggerSource.REHYDRATED,
                SchedulerJobTriggerSource.REHYDRATED,
            ),
            recorder.seenTriggerSources,
        )
    }

    @Test
    fun `run preserves job failure when recorder fails during failure record`() {
        val recorder = ThrowingSchedulerJobExecutionRecorder(throwOnPhase = SchedulerJobExecutionPhase.FAILED)
        val guard = InMemorySchedulerOverlapGuard()
        val runner = SchedulerJobExecutionRunner(recorder, guard, clock)

        val thrown = assertFailsWith<IllegalStateException> {
            runner.run("book-push") {
                throw IllegalStateException("boom")
            }
        }

        assertEquals("boom", thrown.message)
        assertFalse(guard.isLocked("book-push"))
        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.FAILED,
            ),
            recorder.seenPhases,
        )
    }

    private class RecordingSchedulerJobExecutionRecorder : SchedulerJobExecutionRecorder {
        val records = mutableListOf<SchedulerJobExecutionRecord>()

        override fun record(record: SchedulerJobExecutionRecord) {
            records += record
        }
    }

    private class ThrowingSchedulerJobExecutionRecorder(
        private val throwOnPhase: SchedulerJobExecutionPhase? = null,
    ) : SchedulerJobExecutionRecorder {
        val seenPhases = mutableListOf<SchedulerJobExecutionPhase>()
        val seenJobIds = mutableListOf<String>()
        val seenTriggerSources = mutableListOf<SchedulerJobTriggerSource>()

        override fun record(record: SchedulerJobExecutionRecord) {
            seenPhases += record.phase
            seenJobIds += record.jobId
            seenTriggerSources += record.triggerSource
            if (record.phase == throwOnPhase || throwOnPhase == null) {
                throw IllegalStateException("recorder boom")
            }
        }
    }
}
