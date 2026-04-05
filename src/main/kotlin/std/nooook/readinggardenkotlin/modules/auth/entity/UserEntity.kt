package std.nooook.readinggardenkotlin.modules.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "USER")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_no")
    var userNo: Int? = null,
    @Column(name = "user_email", nullable = false, length = 300)
    var userEmail: String = "",
    @Lob
    @Column(name = "user_password", nullable = false)
    var userPassword: String = "",
    @Column(name = "user_created_at", nullable = false)
    var userCreatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "user_nick", nullable = false, length = 30)
    var userNick: String = "",
    @Column(name = "user_image", nullable = false, length = 30)
    var userImage: String = "",
    @Lob
    @Column(name = "user_fcm", nullable = false)
    var userFcm: String = "",
    @Column(name = "user_social_id", nullable = false, length = 100)
    var userSocialId: String = "",
    @Column(name = "user_social_type", nullable = false, length = 30)
    var userSocialType: String = "",
    @Column(name = "user_auth_number", length = 10)
    var userAuthNumber: String? = null,
)
