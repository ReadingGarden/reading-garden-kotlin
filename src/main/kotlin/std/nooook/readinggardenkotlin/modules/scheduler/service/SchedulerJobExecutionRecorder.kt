package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

interface SchedulerJobExecutionRecorder {
    fun record(record: SchedulerJobExecutionRecord)
}

enum class SchedulerJobTriggerSource {
    SCHEDULED,
    REHYDRATED,
    AFTER_COMMIT,
    MANUAL,
}

enum class SchedulerJobExecutionPhase {
    STARTED,
    SUCCEEDED,
    FAILED,
    SKIPPED,
}

data class SchedulerJobExecutionRecord(
    val jobName: String,
    val jobId: String,
    val triggerSource: SchedulerJobTriggerSource,
    val phase: SchedulerJobExecutionPhase,
    val occurredAt: Instant,
    val startedAt: Instant? = null,
    val reason: String? = null,
    val failure: Throwable? = null,
    val context: Map<String, String> = emptyMap(),
) {
    companion object {
        fun started(
            jobName: String,
            jobId: String,
            triggerSource: SchedulerJobTriggerSource,
            startedAt: Instant,
            context: Map<String, String> = emptyMap(),
        ): SchedulerJobExecutionRecord =
            SchedulerJobExecutionRecord(
                jobName = jobName,
                jobId = jobId,
                triggerSource = triggerSource,
                phase = SchedulerJobExecutionPhase.STARTED,
                occurredAt = startedAt,
                context = context,
            )

        fun succeeded(
            jobName: String,
            jobId: String,
            triggerSource: SchedulerJobTriggerSource,
            startedAt: Instant,
            finishedAt: Instant,
            context: Map<String, String> = emptyMap(),
        ): SchedulerJobExecutionRecord =
            SchedulerJobExecutionRecord(
                jobName = jobName,
                jobId = jobId,
                triggerSource = triggerSource,
                phase = SchedulerJobExecutionPhase.SUCCEEDED,
                occurredAt = finishedAt,
                startedAt = startedAt,
                context = context,
            )

        fun failed(
            jobName: String,
            jobId: String,
            triggerSource: SchedulerJobTriggerSource,
            startedAt: Instant,
            finishedAt: Instant,
            failure: Throwable,
            context: Map<String, String> = emptyMap(),
        ): SchedulerJobExecutionRecord =
            SchedulerJobExecutionRecord(
                jobName = jobName,
                jobId = jobId,
                triggerSource = triggerSource,
                phase = SchedulerJobExecutionPhase.FAILED,
                occurredAt = finishedAt,
                startedAt = startedAt,
                failure = failure,
                context = context,
            )

        fun skipped(
            jobName: String,
            jobId: String,
            triggerSource: SchedulerJobTriggerSource,
            occurredAt: Instant,
            reason: String,
            context: Map<String, String> = emptyMap(),
        ): SchedulerJobExecutionRecord =
            SchedulerJobExecutionRecord(
                jobName = jobName,
                jobId = jobId,
                triggerSource = triggerSource,
                phase = SchedulerJobExecutionPhase.SKIPPED,
                occurredAt = occurredAt,
                reason = reason,
                context = context,
            )
    }
}

@Service
class LoggingSchedulerJobExecutionRecorder : SchedulerJobExecutionRecorder {
    override fun record(record: SchedulerJobExecutionRecord) {
        when (record.phase) {
            SchedulerJobExecutionPhase.STARTED -> logger.info(
                "Scheduler job started: jobName={}, jobId={}, triggerSource={}, context={}",
                record.jobName,
                record.jobId,
                record.triggerSource,
                record.context,
            )
            SchedulerJobExecutionPhase.SUCCEEDED -> logger.info(
                "Scheduler job succeeded: jobName={}, jobId={}, triggerSource={}, context={}",
                record.jobName,
                record.jobId,
                record.triggerSource,
                record.context,
            )
            SchedulerJobExecutionPhase.FAILED -> logger.warn(
                "Scheduler job failed: jobName={}, jobId={}, triggerSource={}, context={}",
                record.jobName,
                record.jobId,
                record.triggerSource,
                record.context,
                record.failure,
            )
            SchedulerJobExecutionPhase.SKIPPED -> logger.info(
                "Scheduler job skipped: jobName={}, jobId={}, triggerSource={}, reason={}, context={}",
                record.jobName,
                record.jobId,
                record.triggerSource,
                record.reason ?: "unknown reason",
                record.context,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingSchedulerJobExecutionRecorder::class.java)
    }
}
