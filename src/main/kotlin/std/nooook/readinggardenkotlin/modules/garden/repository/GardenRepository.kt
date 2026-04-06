package std.nooook.readinggardenkotlin.modules.garden.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity

interface GardenRepository : JpaRepository<GardenEntity, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select garden from GardenEntity garden where garden.gardenNo = :gardenNo")
    fun findByGardenNoForUpdate(
        @Param("gardenNo") gardenNo: Int,
    ): GardenEntity?
}
