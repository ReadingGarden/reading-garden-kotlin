package std.nooook.readinggardenkotlin.modules.memo.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity

interface MemoRepository : JpaRepository<MemoEntity, Int> {
    fun findAllByUserNo(userNo: Int): List<MemoEntity>

    fun findAllByUserNoOrderByMemoLikeDescMemoCreatedAtDesc(
        userNo: Int,
        pageable: Pageable,
    ): Page<MemoEntity>
}
