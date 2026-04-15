package std.nooook.readinggardenkotlin.modules.push.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity

interface PushSettingsRepository : JpaRepository<PushSettingsEntity, Long> {
    fun findByUserId(userId: Long): PushSettingsEntity?
    fun findAllByBookOkTrueAndPushTimeIsNotNull(): List<PushSettingsEntity>
    fun findAllByAppOkTrue(): List<PushSettingsEntity>
}
