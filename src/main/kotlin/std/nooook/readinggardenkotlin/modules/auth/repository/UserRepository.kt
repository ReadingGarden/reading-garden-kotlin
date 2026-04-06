package std.nooook.readinggardenkotlin.modules.auth.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Int> {
    fun findByUserNo(userNo: Int): UserEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.userNo = :userNo")
    fun findByUserNoForUpdate(
        @Param("userNo") userNo: Int,
    ): UserEntity?

    fun findByUserEmail(userEmail: String): UserEntity?

    fun findByUserSocialIdAndUserSocialType(userSocialId: String, userSocialType: String): UserEntity?

    fun existsByUserEmail(userEmail: String): Boolean

    fun existsByUserSocialIdAndUserSocialType(userSocialId: String, userSocialType: String): Boolean
}
