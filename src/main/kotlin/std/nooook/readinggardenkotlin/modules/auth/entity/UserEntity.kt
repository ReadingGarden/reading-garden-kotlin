package std.nooook.readinggardenkotlin.modules.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 300)
    var email: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var password: String = "",
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, length = 30)
    var nick: String = "",
    @Column(nullable = false, length = 30)
    var image: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var fcm: String = "",
    @Column(nullable = false, length = 100)
    var socialId: String = "",
    @Column(nullable = false, length = 30)
    var socialType: String = "",
    @Column(length = 10)
    var authNumber: String? = null,
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
