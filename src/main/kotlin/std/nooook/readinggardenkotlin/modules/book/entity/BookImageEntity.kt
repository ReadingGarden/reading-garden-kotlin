package std.nooook.readinggardenkotlin.modules.book.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "BOOK_IMAGE")
class BookImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,
    @Lob
    @Column(name = "image_name", nullable = false)
    var imageName: String = "",
    @Lob
    @Column(name = "image_url", nullable = false)
    var imageUrl: String = "",
    @Column(name = "image_created_at", nullable = false)
    var imageCreatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "book_no", nullable = false)
    var bookNo: Int = 0,
)
