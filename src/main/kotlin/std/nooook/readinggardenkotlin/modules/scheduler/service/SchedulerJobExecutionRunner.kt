package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class SchedulerJobExecutionRunner(
    private val recorder: SchedulerJobExecutionRecorder,
    private val overlapGuard: SchedulerOverlapGuard,
    private val utcClock: Clock,
) {
    fun run(
        jobName: String,
        jobId: String = jobName,
        overlapKey: String = jobName,
        triggerSource: SchedulerJobTriggerSource = SchedulerJobTriggerSource.SCHEDULED,
        context: Map<String, String> = emptyMap(),
        block: () -> Unit,
    ) {
        val lease = overlapGuard.tryLock(overlapKey)
        if (lease == null) {
            recordSafely(
                SchedulerJobExecutionRecord.skipped(
                    jobName = jobName,
                    jobId = jobId,
                    triggerSource = triggerSource,
                    occurredAt = utcClock.instant(),
                    reason = "overlap",
                    context = context,
                ),
            )
            return
        }

        val startedAt = utcClock.instant()
        recordSafely(
            SchedulerJobExecutionRecord.started(
                jobName = jobName,
                jobId = jobId,
                triggerSource = triggerSource,
                startedAt = startedAt,
                context = context,
            ),
        )

        try {
            block()
            recordSafely(
                SchedulerJobExecutionRecord.succeeded(
                    jobName = jobName,
                    jobId = jobId,
                    triggerSource = triggerSource,
                    startedAt = startedAt,
                    finishedAt = utcClock.instant(),
                    context = context,
                ),
            )
        } catch (throwable: Throwable) {
            recordSafely(
                SchedulerJobExecutionRecord.failed(
                    jobName = jobName,
                    jobId = jobId,
                    triggerSource = triggerSource,
                    startedAt = startedAt,
                    finishedAt = utcClock.instant(),
                    failure = throwable,
                    context = context,
                ),
            )
            throw throwable
        } finally {
            runCatching { lease.close() }
                .onFailure { exception ->
                    logger.warn("Failed to release scheduler lease for {}.", overlapKey, exception)
                }
        }
    }

    private fun recordSafely(record: SchedulerJobExecutionRecord) {
        runCatching {
            recorder.record(record)
        }.onFailure { exception ->
            logger.warn(
                "Scheduler recorder failed for {} at {}.",
                record.jobName,
                record.phase,
                exception,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SchedulerJobExecutionRunner::class.java)
    }
}
