package std.nooook.readinggardenkotlin.modules.scheduler.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity

@Entity
@Table(name = "scheduled_jobs")
class ScheduledJobEntity(
    @Id
    @Column(length = 191)
    var id: String = "",
    @Column(nullable = false, length = 50)
    var jobType: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    var targetUser: UserEntity? = null,
    @Column(nullable = false)
    var scheduledAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(columnDefinition = "JSONB")
    var payload: String? = null,
    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
