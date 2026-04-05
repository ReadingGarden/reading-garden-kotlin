package std.nooook.readinggardenkotlin.memo.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "MEMO")
class MemoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,
    @Column(name = "book_no", nullable = false)
    var bookNo: Int = 0,
    @Lob
    @Column(name = "memo_content", nullable = false)
    var memoContent: String = "",
    @Column(name = "memo_created_at", nullable = false)
    var memoCreatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "user_no", nullable = false)
    var userNo: Int = 0,
    @Column(name = "memo_like", nullable = false)
    var memoLike: Boolean = false,
    @Lob
    @Column(name = "memo_quote")
    var memoQuote: String? = null,
)
