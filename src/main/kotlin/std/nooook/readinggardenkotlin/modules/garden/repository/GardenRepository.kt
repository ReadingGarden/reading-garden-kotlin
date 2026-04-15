package std.nooook.readinggardenkotlin.modules.garden.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity

interface GardenRepository : JpaRepository<GardenEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GardenEntity g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): GardenEntity?
}
