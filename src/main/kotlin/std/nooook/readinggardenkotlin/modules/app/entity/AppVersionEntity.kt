package std.nooook.readinggardenkotlin.modules.app.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "app_version")
class AppVersionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, unique = true, length = 10)
    val platform: String,
    @Column(name = "latest_version", nullable = false, length = 20)
    var latestVersion: String,
    @Column(name = "min_supported_version", nullable = false, length = 20)
    var minSupportedVersion: String,
    @Column(name = "store_url", nullable = false, length = 500)
    var storeUrl: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    protected constructor() : this(
        platform = "",
        latestVersion = "",
        minSupportedVersion = "",
        storeUrl = "",
    )
}
