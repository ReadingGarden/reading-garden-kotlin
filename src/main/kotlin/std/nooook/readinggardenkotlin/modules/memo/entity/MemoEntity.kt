package std.nooook.readinggardenkotlin.modules.memo.entity

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
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity

@Entity
@Table(name = "memos")
class MemoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    var book: BookEntity,
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = "",
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false)
    var isLiked: Boolean = false,
    @Column(columnDefinition = "TEXT")
    var quote: String? = null,
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    protected constructor() : this(book = BookEntity(user = UserEntity()), user = UserEntity())
}
