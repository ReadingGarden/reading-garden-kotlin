package std.nooook.readinggardenkotlin.modules.book.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "BOOK")
class BookEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_no")
    var bookNo: Int? = null,
    @Column(name = "garden_no")
    var gardenNo: Int? = null,
    @Column(name = "book_title", nullable = false, length = 300)
    var bookTitle: String = "",
    @Column(name = "book_author", nullable = false, length = 100)
    var bookAuthor: String = "",
    @Column(name = "book_publisher", nullable = false, length = 100)
    var bookPublisher: String = "",
    @Column(name = "book_status", nullable = false)
    var bookStatus: Int = 0,
    @Column(name = "user_no", nullable = false)
    var userNo: Int = 0,
    @Column(name = "book_page", nullable = false)
    var bookPage: Int = 0,
    @Column(name = "book_isbn", length = 30)
    var bookIsbn: String? = null,
    @Column(name = "book_tree", length = 30)
    var bookTree: String? = null,
    @Lob
    @Column(name = "book_image_url")
    var bookImageUrl: String? = null,
    @Lob
    @Column(name = "book_info", nullable = false)
    var bookInfo: String = "",
)
