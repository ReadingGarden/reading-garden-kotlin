package std.nooook.readinggardenkotlin.modules.push.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

@Entity
@Table(name = "push_settings")
class PushSettingsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: UserEntity,
    @Column(nullable = false)
    var appOk: Boolean = false,
    @Column(nullable = false)
    var bookOk: Boolean = false,
    var pushTime: LocalDateTime? = null,
) {
    protected constructor() : this(user = UserEntity())
}
