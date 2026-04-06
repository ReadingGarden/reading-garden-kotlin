package std.nooook.readinggardenkotlin.modules.push.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushEntity

interface PushRepository : JpaRepository<PushEntity, Int> {
    fun findByUserNo(userNo: Int): PushEntity?
}
