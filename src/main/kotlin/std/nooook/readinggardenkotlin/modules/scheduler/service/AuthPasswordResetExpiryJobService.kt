package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.scheduler.entity.ApschedulerJobEntity
import std.nooook.readinggardenkotlin.modules.scheduler.repository.ApschedulerJobRepository
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import kotlin.jvm.optionals.getOrNull

@Service
class AuthPasswordResetExpiryJobService(
    private val apschedulerJobRepository: ApschedulerJobRepository,
    private val userRepository: UserRepository,
    private val taskScheduler: TaskScheduler,
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
        userNo: Int,
        authNumber: String,
    ) {
        val job = PasswordResetExpiryJob(
            type = JOB_TYPE,
            version = JOB_VERSION,
            userNo = userNo,
            authNumber = authNumber,
            runAt = Instant.now(utcClock).plus(passwordResetAuthTtl),
        )
        upsertAndSchedule(job)
    }

    fun isPasswordResetAuthValid(
        userNo: Int,
        authNumber: String,
    ): Boolean {
        val job = findPasswordResetExpiryJob(userNo) ?: return false
        if (job.isExpired(Instant.now(utcClock))) {
            expire(job)
            return false
        }
        return job.authNumber == authNumber
    }

    fun findPasswordResetExpiryJob(userNo: Int): PasswordResetExpiryJob? =
        apschedulerJobRepository.findById(jobId(userNo)).getOrNull()?.toPasswordResetJobOrNull()

    fun rehydratePasswordResetExpiryJobs() {
        val persistedJobs = apschedulerJobRepository.findAllByIdStartingWith(JOB_ID_PREFIX)
            .mapNotNull { entity ->
                entity.toPasswordResetJobOrNull() ?: run {
                    cancelScheduledJob(entity.id)
                    apschedulerJobRepository.delete(entity)
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
                expire(job)
            } else {
                schedule(job)
            }
        }
    }

    private fun upsertAndSchedule(job: PasswordResetExpiryJob) {
        apschedulerJobRepository.save(job.toEntity())
        scheduleAfterCommit(job)
    }

    private fun schedule(job: PasswordResetExpiryJob) {
        cancelScheduledJob(job.id)
        scheduledJobs[job.id] = ScheduledJobHandle(
            job = job,
            future = taskScheduler.schedule(
                { expire(job) },
                job.runAt,
            ),
        )
    }

    private fun expire(job: PasswordResetExpiryJob) {
        val persistedJob = findPasswordResetExpiryJob(job.userNo)
        if (persistedJob != job) {
            clearScheduledJobHandle(job)
            return
        }

        clearScheduledJobHandle(job)
        val user = userRepository.findByUserNo(job.userNo)
        if (user != null && user.userAuthNumber == job.authNumber) {
            user.userAuthNumber = null
            userRepository.save(user)
        }
        apschedulerJobRepository.deleteById(job.id)
    }

    private fun scheduleAfterCommit(job: PasswordResetExpiryJob) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule(job)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    schedule(job)
                }
            },
        )
    }

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

    private fun ApschedulerJobEntity.toPasswordResetJobOrNull(): PasswordResetExpiryJob? {
        val nextRunInstant = nextRunTime?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: return null
        val payload = PasswordResetExpiryPayload.parse(jobState) ?: return null
        if (payload.type != JOB_TYPE || payload.version != JOB_VERSION) {
            return null
        }
        return PasswordResetExpiryJob(
            type = payload.type,
            version = payload.version,
            userNo = payload.userNo,
            authNumber = payload.authNumber,
            runAt = nextRunInstant,
        )
    }

    private fun PasswordResetExpiryJob.toEntity(): ApschedulerJobEntity =
        ApschedulerJobEntity(
            id = id,
            nextRunTime = runAt.toEpochMilli() / 1000.0,
            jobState = PasswordResetExpiryPayload(
                type = type,
                version = version,
                userNo = userNo,
                authNumber = authNumber,
            ).toBytes(),
        )

    data class PasswordResetExpiryJob(
        val type: String,
        val version: Int,
        val userNo: Int,
        val authNumber: String,
        val runAt: Instant,
    ) {
        val id: String
            get() = jobId(userNo)

        fun isExpired(now: Instant): Boolean = !now.isBefore(runAt)
    }

    private data class ScheduledJobHandle(
        val job: PasswordResetExpiryJob,
        val future: ScheduledFuture<*>,
    )

    private data class PasswordResetExpiryPayload(
        val type: String,
        val version: Int,
        val userNo: Int,
        val authNumber: String,
    ) {
        fun toBytes(): ByteArray =
            "$type|$version|$userNo|$authNumber".toByteArray(StandardCharsets.UTF_8)

        companion object {
            fun parse(bytes: ByteArray): PasswordResetExpiryPayload? {
                val parts = bytes.toString(StandardCharsets.UTF_8).split('|', limit = 4)
                if (parts.size != 4) {
                    return null
                }
                val version = parts[1].toIntOrNull() ?: return null
                val userNo = parts[2].toIntOrNull() ?: return null
                return PasswordResetExpiryPayload(
                    type = parts[0],
                    version = version,
                    userNo = userNo,
                    authNumber = parts[3],
                )
            }
        }
    }

    companion object {
        private const val JOB_TYPE = "AUTH_PASSWORD_RESET_EXPIRY"
        private const val JOB_VERSION = 1
        private const val JOB_ID_PREFIX = "auth:password-reset-expiry:"

        fun jobId(userNo: Int): String = "$JOB_ID_PREFIX$userNo"
    }
}
