package std.nooook.readinggardenkotlin.modules.book.repository

import org.springframework.data.jpa.repository.JpaRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity

interface BookRepository : JpaRepository<BookEntity, Long> {
    fun countByUserIdAndStatus(userId: Long, status: Int): Long
    fun countByUserIdAndGardenId(userId: Long, gardenId: Long): Long
    fun findAllByUserId(userId: Long): List<BookEntity>
    fun findByIdAndUserId(id: Long, userId: Long): BookEntity?
    fun findByIsbnAndUserId(isbn: String, userId: Long): BookEntity?
    fun countByGardenId(gardenId: Long): Long
    fun findAllByGardenIdOrderByIdAsc(gardenId: Long): List<BookEntity>
    fun findAllByUserIdAndGardenId(userId: Long, gardenId: Long): List<BookEntity>
}
