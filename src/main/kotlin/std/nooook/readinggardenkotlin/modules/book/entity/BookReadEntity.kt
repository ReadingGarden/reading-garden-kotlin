package std.nooook.readinggardenkotlin.modules.book.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "book_reads")
class BookReadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    val book: BookEntity,
    @Column(nullable = false)
    var currentPage: Int = 0,
    var startDate: LocalDateTime? = null,
    var endDate: LocalDateTime? = null,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    protected constructor() : this(book = BookEntity())
}
