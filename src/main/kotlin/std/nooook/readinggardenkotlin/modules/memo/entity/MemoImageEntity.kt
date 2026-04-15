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

@Entity
@Table(name = "memo_images")
class MemoImageEntity(
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
    @JoinColumn(name = "memo_id", nullable = false)
    val memo: MemoEntity,
) {
    protected constructor() : this(memo = MemoEntity(book = std.nooook.readinggardenkotlin.modules.book.entity.BookEntity(user = std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity()), user = std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity()))
}
