package std.nooook.readinggardenkotlin.modules.garden.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity

interface GardenMemberRepository : JpaRepository<GardenMemberEntity, Long> {
    fun countByUserId(userId: Long): Long
    fun countByGardenId(gardenId: Long): Long
    fun existsByGardenIdAndUserId(gardenId: Long, userId: Long): Boolean
    fun findByGardenIdAndUserId(gardenId: Long, userId: Long): GardenMemberEntity?
    fun findFirstByUserIdAndIsMainTrue(userId: Long): GardenMemberEntity?
    fun findAllByUserId(userId: Long): List<GardenMemberEntity>
    fun findAllByUserIdOrderByIsMainDescJoinDateAsc(userId: Long): List<GardenMemberEntity>
    fun findAllByGardenIdOrderByJoinDateAsc(gardenId: Long): List<GardenMemberEntity>
    fun findAllByGardenIdOrderByIsLeaderDescJoinDateAsc(gardenId: Long): List<GardenMemberEntity>
}
