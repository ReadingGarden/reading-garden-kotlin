package std.nooook.readinggardenkotlin.modules.auth.repository

import jakarta.persistence.LockModeType
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update UserEntity u set u.fcm = '' where trim(u.fcm) in :tokens")
    fun clearFcmTokens(@Param("tokens") tokens: Collection<String>): Int
}
