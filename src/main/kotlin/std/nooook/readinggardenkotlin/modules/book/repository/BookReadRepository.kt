package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity

interface BookReadRepository : JpaRepository<BookReadEntity, Long> {
    fun deleteAllByBookId(bookId: Long)
    fun findTopByBookIdOrderByCreatedAtDesc(bookId: Long): BookReadEntity?
    fun findAllByBookIdOrderByCreatedAtDesc(bookId: Long): List<BookReadEntity>
}
