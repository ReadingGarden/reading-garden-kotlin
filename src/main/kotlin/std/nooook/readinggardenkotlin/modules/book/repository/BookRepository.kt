package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity

interface BookRepository : JpaRepository<BookEntity, Int> {
    fun countByUserNoAndBookStatus(userNo: Int, bookStatus: Int): Long

    fun findAllByUserNo(userNo: Int): List<BookEntity>
}
