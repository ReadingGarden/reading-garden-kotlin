package std.nooook.readinggardenkotlin.garden.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "GARDEN")
class GardenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "garden_no")
    var gardenNo: Int? = null,
    @Column(name = "garden_title", nullable = false, length = 30)
    var gardenTitle: String = "",
    @Column(name = "garden_info", nullable = false, length = 200)
    var gardenInfo: String = "",
    @Column(name = "garden_color", nullable = false, length = 20)
    var gardenColor: String = "",
    @Column(name = "garden_created_at", nullable = false)
    var gardenCreatedAt: LocalDateTime = LocalDateTime.now(),
)
