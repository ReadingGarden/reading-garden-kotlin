package std.nooook.readinggardenkotlin.modules.push.controller

import java.time.LocalDateTime

data class PushRequest(
    val push_app_ok: Boolean?,
    val push_book_ok: Boolean?,
    val push_time: LocalDateTime?,
)
