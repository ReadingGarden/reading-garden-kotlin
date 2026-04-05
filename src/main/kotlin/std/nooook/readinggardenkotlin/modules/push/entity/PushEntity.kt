package std.nooook.readinggardenkotlin.modules.push.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "PUSH")
class PushEntity(
    @Id
    @Column(name = "user_no")
    var userNo: Int = 0,
    @Column(name = "push_app_ok", nullable = false, columnDefinition = "tinyint(1)")
    var pushAppOk: Boolean = false,
    @Column(name = "push_book_ok", nullable = false, columnDefinition = "tinyint(1)")
    var pushBookOk: Boolean = false,
    @Column(name = "push_time")
    var pushTime: LocalDateTime? = null,
)
