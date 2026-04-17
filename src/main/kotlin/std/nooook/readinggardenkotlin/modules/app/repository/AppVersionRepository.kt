package std.nooook.readinggardenkotlin.modules.app.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.app.entity.AppVersionEntity

interface AppVersionRepository : JpaRepository<AppVersionEntity, Long> {
    fun findByPlatform(platform: String): AppVersionEntity?
}
