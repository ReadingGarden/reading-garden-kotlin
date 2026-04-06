package std.nooook.readinggardenkotlin.modules.auth.controller

import java.time.LocalDateTime

data class SignupResponse(
    val access_token: String,
    val refresh_token: String,
    val user_nick: String,
)

data class UserProfileResponse(
    val user_no: Int,
    val user_nick: String,
    val user_email: String,
    val user_social_type: String,
    val user_image: String,
    val user_created_at: LocalDateTime,
    val garden_count: Long,
    val read_book_count: Long,
    val like_book_count: Long,
)

data class UserSummaryResponse(
    val user_no: Int,
    val user_nick: String,
    val user_email: String,
    val user_image: String,
    val user_fcm: String,
    val user_social_id: String,
    val user_social_type: String,
    val user_created_at: LocalDateTime,
)
