package std.nooook.readinggardenkotlin.modules.scheduler.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemorySchedulerOverlapGuardTest {
    @Test
    fun `tryLock returns null while same job is active and reopens after close`() {
        val guard = InMemorySchedulerOverlapGuard()

        val firstLease = guard.tryLock("book-push")

        requireNotNull(firstLease)
        assertTrue(guard.isLocked("book-push"))
        assertNull(guard.tryLock("book-push"))

        firstLease.close()

        assertFalse(guard.isLocked("book-push"))

        val secondLease = guard.tryLock("book-push")

        requireNotNull(secondLease)
        assertTrue(guard.isLocked("book-push"))

        secondLease.close()
        assertFalse(guard.isLocked("book-push"))
    }
}
