package std.nooook.readinggardenkotlin.modules.garden.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenUserEntity

interface GardenUserRepository : JpaRepository<GardenUserEntity, Int> {
    fun countByUserNo(userNo: Int): Long

    fun countByGardenNo(gardenNo: Int): Long

    fun findAllByUserNo(userNo: Int): List<GardenUserEntity>

    fun findAllByGardenNoOrderByGardenSignDateAsc(gardenNo: Int): List<GardenUserEntity>
}
