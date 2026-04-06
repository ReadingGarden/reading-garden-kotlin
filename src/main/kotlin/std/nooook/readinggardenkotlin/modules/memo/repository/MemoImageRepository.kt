package std.nooook.readinggardenkotlin.modules.memo.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity

interface MemoImageRepository : JpaRepository<MemoImageEntity, Int> {
    fun deleteByMemoNo(memoNo: Int)

    fun findAllByMemoNoIn(memoNos: Collection<Int>): List<MemoImageEntity>

    fun findByMemoNo(memoNo: Int): MemoImageEntity?

    fun findFirstByMemoNoOrderByImageCreatedAtDesc(memoNo: Int): MemoImageEntity?
}
