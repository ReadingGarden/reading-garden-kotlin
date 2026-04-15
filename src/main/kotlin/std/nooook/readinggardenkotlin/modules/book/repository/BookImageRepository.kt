package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity

interface BookImageRepository : JpaRepository<BookImageEntity, Long> {
    fun deleteByBookId(bookId: Long)
    fun findAllByBookId(bookId: Long): List<BookImageEntity>
    fun findByBookId(bookId: Long): BookImageEntity?
}
