package std.nooook.readinggardenkotlin.modules.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Int> {
    fun findByUserNo(userNo: Int): UserEntity?

    fun findByUserEmail(userEmail: String): UserEntity?

    fun findByUserSocialIdAndUserSocialType(userSocialId: String, userSocialType: String): UserEntity?

    fun existsByUserEmail(userEmail: String): Boolean

    fun existsByUserSocialIdAndUserSocialType(userSocialId: String, userSocialType: String): Boolean
}
