package std.nooook.readinggardenkotlin.modules.memo.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity

interface MemoRepository : JpaRepository<MemoEntity, Long> {
    fun findAllByUserId(userId: Long): List<MemoEntity>
    fun findAllByBookId(bookId: Long): List<MemoEntity>
    fun findByIdAndUserId(id: Long, userId: Long): MemoEntity?
    fun findAllByBookIdOrderByIsLikedDescCreatedAtDesc(bookId: Long): List<MemoEntity>

    @Query(
        value = """
            select m
            from MemoEntity m
            join m.book b
            where m.user.id = :userId
            order by m.isLiked desc, m.createdAt desc
        """,
        countQuery = """
            select count(m)
            from MemoEntity m
            join m.book b
            where m.user.id = :userId
        """,
    )
    fun findAllByUserIdJoinBookOrderByIsLikedDescCreatedAtDesc(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<MemoEntity>
}
