package std.nooook.readinggardenkotlin.modules.garden.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity

interface GardenRepository : JpaRepository<GardenEntity, Int>
