package std.nooook.readinggardenkotlin.modules.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.auth.entity.RefreshTokenEntity

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Int> {
    fun findByUserNo(userNo: Int): RefreshTokenEntity?

    fun findByUserNoAndToken(userNo: Int, token: String): RefreshTokenEntity?

    fun deleteByUserNo(userNo: Int)
}
