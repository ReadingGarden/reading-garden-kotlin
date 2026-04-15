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
@Table(name = "book_images")
class BookImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var name: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var url: String = "",
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    val book: BookEntity,
) {
    protected constructor() : this(book = BookEntity())
}
