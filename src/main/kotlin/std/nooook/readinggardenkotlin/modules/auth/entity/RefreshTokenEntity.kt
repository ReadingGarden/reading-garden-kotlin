package std.nooook.readinggardenkotlin.modules.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "REFRESH_TOKEN")
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,
    @Column(name = "user_no", nullable = false)
    var userNo: Int = 0,
    @Column(name = "token", columnDefinition = "text")
    var token: String? = null,
    @Column(name = "exp")
    var exp: LocalDateTime? = null,
)
