package std.nooook.readinggardenkotlin.modules.garden.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "GARDEN_USER")
class GardenUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int? = null,
    @Column(name = "garden_no", nullable = false)
    var gardenNo: Int = 0,
    @Column(name = "user_no", nullable = false)
    var userNo: Int = 0,
    @Column(name = "garden_leader", nullable = false)
    var gardenLeader: Boolean = false,
    @Column(name = "garden_sign_date", nullable = false)
    var gardenSignDate: LocalDateTime = LocalDateTime.now(),
    @Column(name = "garden_main", nullable = false)
    var gardenMain: Boolean = false,
)
