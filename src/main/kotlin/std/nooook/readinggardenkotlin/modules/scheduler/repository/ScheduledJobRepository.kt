package std.nooook.readinggardenkotlin.modules.scheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.scheduler.entity.ScheduledJobEntity

interface ScheduledJobRepository : JpaRepository<ScheduledJobEntity, String> {
    fun findAllByIdStartingWith(prefix: String): List<ScheduledJobEntity>
    fun findAllByJobType(jobType: String): List<ScheduledJobEntity>
}
