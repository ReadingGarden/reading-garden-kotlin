package std.nooook.readinggardenkotlin.modules.garden.entity

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

@Entity
@Table(name = "garden_members")
class GardenMemberEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id", nullable = false)
    val garden: GardenEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false)
    var isLeader: Boolean = false,
    @Column(nullable = false)
    var joinDate: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    var isMain: Boolean = false,
) {
    protected constructor() : this(garden = GardenEntity(), user = UserEntity())
}
