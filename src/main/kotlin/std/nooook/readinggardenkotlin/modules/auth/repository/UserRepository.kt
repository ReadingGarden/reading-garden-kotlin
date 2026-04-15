package std.nooook.readinggardenkotlin.modules.auth.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findAllByIdIn(ids: Collection<Long>): List<UserEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): UserEntity?

    fun findByEmail(email: String): UserEntity?

    fun findBySocialIdAndSocialType(socialId: String, socialType: String): UserEntity?

    fun existsByEmail(email: String): Boolean

    fun existsBySocialIdAndSocialType(socialId: String, socialType: String): Boolean
}
