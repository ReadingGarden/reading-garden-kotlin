package std.nooook.readinggardenkotlin.modules.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.auth.entity.RefreshTokenEntity

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByUserId(userId: Long): RefreshTokenEntity?

    fun findAllByUserId(userId: Long): List<RefreshTokenEntity>

    fun findByUserIdAndToken(userId: Long, token: String): RefreshTokenEntity?

    fun deleteByUserId(userId: Long)

    fun deleteAllByUserId(userId: Long)
}
