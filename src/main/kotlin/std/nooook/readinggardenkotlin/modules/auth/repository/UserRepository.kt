package std.nooook.readinggardenkotlin.modules.auth.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Int> {
    fun findByUserNo(userNo: Int): UserEntity?
}
