package std.nooook.readinggardenkotlin.book.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "BOOK_READ")
class BookReadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,
    @Column(name = "book_no", nullable = false)
    var bookNo: Int = 0,
    @Column(name = "book_current_page", nullable = false)
    var bookCurrentPage: Int = 0,
    @Column(name = "book_start_date")
    var bookStartDate: LocalDateTime? = null,
    @Column(name = "book_end_date")
    var bookEndDate: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "user_no", nullable = false)
    var userNo: Int = 0,
)
