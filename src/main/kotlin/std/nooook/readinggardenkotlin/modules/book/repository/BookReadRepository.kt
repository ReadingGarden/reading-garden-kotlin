package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity

interface BookReadRepository : JpaRepository<BookReadEntity, Int> {
    fun deleteAllByBookNo(bookNo: Int)
}
