package std.nooook.readinggardenkotlin.modules.memo.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity

interface MemoImageRepository : JpaRepository<MemoImageEntity, Long> {
    fun deleteByMemoId(memoId: Long)
    fun findAllByMemoIdIn(memoIds: Collection<Long>): List<MemoImageEntity>
    fun findFirstByMemoIdOrderByCreatedAtDesc(memoId: Long): MemoImageEntity?
}
