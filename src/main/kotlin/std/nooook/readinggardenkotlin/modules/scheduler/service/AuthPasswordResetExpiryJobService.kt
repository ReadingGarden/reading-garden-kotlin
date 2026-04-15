package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.slf4j.LoggerFactory
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.scheduler.entity.ScheduledJobEntity
import std.nooook.readinggardenkotlin.modules.scheduler.repository.ScheduledJobRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import kotlin.jvm.optionals.getOrNull

@Service
class AuthPasswordResetExpiryJobService(
    private val scheduledJobRepository: ScheduledJobRepository,
    private val userRepository: UserRepository,
    private val taskScheduler: TaskScheduler,
    private val schedulerJobExecutionRunner: SchedulerJobExecutionRunner,
    private val utcClock: Clock,
    @Value("\${app.auth.password-reset-auth-ttl:PT5M}")
    private val passwordResetAuthTtl: Duration,
) {
    private val scheduledJobs = ConcurrentHashMap<String, ScheduledJobHandle>()

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        rehydratePasswordResetExpiryJobs()
    }

    fun schedulePasswordResetExpiry(
        userId: Long,
        authNumber: String,
    ) {
        val job = PasswordResetExpiryJob(
            type = JOB_TYPE,
            version = JOB_VERSION,
            userId = userId,
            authNumber = authNumber,
            runAt = Instant.now(utcClock).plus(passwordResetAuthTtl),
        )
        upsertAndSchedule(job)
    }

    fun isPasswordResetAuthValid(
        userId: Long,
        authNumber: String,
    ): Boolean {
        val job = findPasswordResetExpiryJob(userId) ?: return false
        if (job.isExpired(Instant.now(utcClock))) {
            expire(job)
            return false
        }
        return job.authNumber == authNumber
    }

    fun findPasswordResetExpiryJob(userId: Long): PasswordResetExpiryJob? {
        val entity = scheduledJobRepository.findById(jobId(userId)).getOrNull() ?: return null
        return entity.toPasswordResetJobOrNull()
    }

    fun rehydratePasswordResetExpiryJobs() {
        val persistedJobs = scheduledJobRepository.findAllByIdStartingWith(JOB_ID_PREFIX)
            .mapNotNull { entity ->
                entity.toPasswordResetJobOrNull() ?: run {
                    deleteMalformedJob(entity)
                    null
                }
            }

        val persistedIds = persistedJobs.mapTo(mutableSetOf()) { it.id }
        scheduledJobs.keys
            .filter { it.startsWith(JOB_ID_PREFIX) && it !in persistedIds }
            .forEach(::cancelScheduledJob)

        val now = Instant.now(utcClock)
        persistedJobs.forEach { job ->
            if (job.isExpired(now)) {
                executeExpiry(
                    job = job,
                    triggerSource = SchedulerJobTriggerSource.REHYDRATED,
                    context = jobContext(job) + ("rehydrate_action" to "expire_overdue"),
                )
            } else {
                schedule(job, SchedulerJobTriggerSource.REHYDRATED)
            }
        }
    }

    private fun upsertAndSchedule(job: PasswordResetExpiryJob) {
        val user = userRepository.findById(job.userId).orElse(null)
        scheduledJobRepository.save(
            ScheduledJobEntity(
                id = job.id,
                jobType = JOB_TYPE,
                targetUser = user,
                scheduledAt = OffsetDateTime.ofInstant(job.runAt, ZoneOffset.UTC),
                payload = """{"type":"${job.type}","version":${job.version},"authNumber":"${job.authNumber}"}""",
            ),
        )
        scheduleAfterCommit(job)
    }

    private fun schedule(
        job: PasswordResetExpiryJob,
        triggerSource: SchedulerJobTriggerSource,
    ) {
        cancelScheduledJob(job.id)
        scheduledJobs[job.id] = ScheduledJobHandle(
            job = job,
            future = taskScheduler.schedule(
                { executeExpiry(job, triggerSource, jobContext(job)) },
                job.runAt,
            ),
        )
    }

    private fun executeExpiry(
        job: PasswordResetExpiryJob,
        triggerSource: SchedulerJobTriggerSource,
        context: Map<String, String>,
    ) {
        schedulerJobExecutionRunner.run(
            jobName = JOB_NAME,
            jobId = job.id,
            overlapKey = job.id,
            triggerSource = triggerSource,
            context = context,
        ) {
            expire(job)
        }
    }

    private fun expire(job: PasswordResetExpiryJob) {
        val persistedJob = findPasswordResetExpiryJob(job.userId)
        if (persistedJob != job) {
            clearScheduledJobHandle(job)
            return
        }

        clearScheduledJobHandle(job)
        val user = userRepository.findById(job.userId).orElse(null)
        if (user != null && user.authNumber == job.authNumber) {
            user.authNumber = null
            userRepository.save(user)
        }
        scheduledJobRepository.deleteById(job.id)
    }

    private fun scheduleAfterCommit(job: PasswordResetExpiryJob) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule(job, SchedulerJobTriggerSource.AFTER_COMMIT)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    schedule(job, SchedulerJobTriggerSource.AFTER_COMMIT)
                }
            },
        )
    }

    private fun deleteMalformedJob(entity: ScheduledJobEntity) {
        cancelScheduledJob(entity.id)
        scheduledJobRepository.delete(entity)
        logger.warn("Deleted malformed password reset expiry job during rehydrate: {}", entity.id)
    }

    private fun ScheduledJobEntity.toPasswordResetJobOrNull(): PasswordResetExpiryJob? {
        if (jobType != JOB_TYPE) return null
        val userId = targetUser?.id ?: return null
        val payloadStr = payload ?: return null
        // Parse simple JSON payload: {"type":"...","version":N,"authNumber":"..."}
        val authNumber = AUTH_NUMBER_REGEX.find(payloadStr)?.groupValues?.get(1) ?: return null
        val version = VERSION_REGEX.find(payloadStr)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (version != JOB_VERSION) return null
        return PasswordResetExpiryJob(
            type = JOB_TYPE,
            version = version,
            userId = userId,
            authNumber = authNumber,
            runAt = scheduledAt.toInstant(),
        )
    }

    private fun jobContext(job: PasswordResetExpiryJob): Map<String, String> =
        mapOf(
            "job_type" to "auth_password_reset_expiry",
            "user_id" to job.userId.toString(),
            "run_at_epoch_millis" to job.runAt.toEpochMilli().toString(),
        )

    private fun clearScheduledJobHandle(job: PasswordResetExpiryJob) {
        val handle = scheduledJobs[job.id] ?: return
        if (handle.job != job) {
            return
        }
        if (scheduledJobs.remove(job.id, handle)) {
            handle.future.cancel(false)
        }
    }

    private fun cancelScheduledJob(jobId: String) {
        scheduledJobs.remove(jobId)?.future?.cancel(false)
    }

    data class PasswordResetExpiryJob(
        val type: String,
        val version: Int,
        val userId: Long,
        val authNumber: String,
        val runAt: Instant,
    ) {
        val id: String
            get() = jobId(userId)

        fun isExpired(now: Instant): Boolean = !now.isBefore(runAt)
    }

    private data class ScheduledJobHandle(
        val job: PasswordResetExpiryJob,
        val future: ScheduledFuture<*>,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(AuthPasswordResetExpiryJobService::class.java)
        private const val JOB_NAME = "auth-password-reset-expiry"
        private const val JOB_TYPE = "AUTH_PASSWORD_RESET_EXPIRY"
        private const val JOB_VERSION = 1
        private const val JOB_ID_PREFIX = "auth:password-reset-expiry:"
        private val AUTH_NUMBER_REGEX = Regex(""""authNumber"\s*:\s*"([^"]+)"""")
        private val VERSION_REGEX = Regex(""""version"\s*:\s*(\d+)""")

        fun jobId(userId: Long): String = "$JOB_ID_PREFIX$userId"
    }
}
