package std.nooook.readinggardenkotlin.modules.memo.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity

interface MemoRepository : JpaRepository<MemoEntity, Int> {
    fun findAllByUserNo(userNo: Int): List<MemoEntity>

    fun findAllByBookNo(bookNo: Int): List<MemoEntity>

    @Query(
        value = """
            select memo
            from MemoEntity memo
            join BookEntity book on book.bookNo = memo.bookNo
            where memo.userNo = :userNo
            order by memo.memoLike desc, memo.memoCreatedAt desc
        """,
        countQuery = """
            select count(memo)
            from MemoEntity memo
            join BookEntity book on book.bookNo = memo.bookNo
            where memo.userNo = :userNo
        """,
    )
    fun findAllByUserNoJoinBookOrderByMemoLikeDescMemoCreatedAtDesc(
        @Param("userNo") userNo: Int,
        pageable: Pageable,
    ): Page<MemoEntity>

    fun findAllByUserNoOrderByMemoLikeDescMemoCreatedAtDesc(
        userNo: Int,
        pageable: Pageable,
    ): Page<MemoEntity>
}
