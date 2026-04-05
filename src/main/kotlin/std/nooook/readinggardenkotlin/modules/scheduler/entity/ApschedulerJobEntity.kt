package std.nooook.readinggardenkotlin.modules.scheduler.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "apscheduler_jobs")
class ApschedulerJobEntity(
    @Id
    @Column(name = "id", length = 191)
    var id: String = "",
    @Column(name = "next_run_time")
    var nextRunTime: Double? = null,
    @Lob
    @Column(name = "job_state", nullable = false)
    var jobState: ByteArray = byteArrayOf(),
)
