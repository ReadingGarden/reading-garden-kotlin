package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface SchedulerOverlapGuard {
    fun tryLock(jobName: String): SchedulerOverlapLease?
}

interface SchedulerOverlapLease : AutoCloseable {
    val jobName: String
}

@Service
class InMemorySchedulerOverlapGuard : SchedulerOverlapGuard {
    private val activeJobNames = ConcurrentHashMap.newKeySet<String>()

    override fun tryLock(jobName: String): SchedulerOverlapLease? {
        if (!activeJobNames.add(jobName)) {
            return null
        }
        return Lease(jobName)
    }

    fun isLocked(jobName: String): Boolean = activeJobNames.contains(jobName)

    private inner class Lease(
        override val jobName: String,
    ) : SchedulerOverlapLease {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            activeJobNames.remove(jobName)
        }
    }
}
