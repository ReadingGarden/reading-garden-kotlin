package std.nooook.readinggardenkotlin.modules.scheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.scheduler.entity.ApschedulerJobEntity

interface ApschedulerJobRepository : JpaRepository<ApschedulerJobEntity, String> {
    fun findAllByIdStartingWith(prefix: String): List<ApschedulerJobEntity>
}
