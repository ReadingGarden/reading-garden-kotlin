package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity

interface BookRepository : JpaRepository<BookEntity, Int> {
    fun countByUserNoAndBookStatus(userNo: Int, bookStatus: Int): Long

    fun findAllByUserNo(userNo: Int): List<BookEntity>

    fun findByBookNoAndUserNo(bookNo: Int, userNo: Int): BookEntity?

    fun findByBookIsbnAndUserNo(bookIsbn: String, userNo: Int): BookEntity?

    fun countByGardenNo(gardenNo: Int): Long

    fun findAllByUserNoAndGardenNo(userNo: Int, gardenNo: Int): List<BookEntity>
}
