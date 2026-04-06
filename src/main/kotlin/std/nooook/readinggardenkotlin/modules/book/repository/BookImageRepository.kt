package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity

interface BookImageRepository : JpaRepository<BookImageEntity, Int> {
    fun deleteByBookNo(bookNo: Int)

    fun findByBookNo(bookNo: Int): BookImageEntity?
}
