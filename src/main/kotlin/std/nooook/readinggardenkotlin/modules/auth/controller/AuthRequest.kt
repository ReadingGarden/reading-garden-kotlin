package std.nooook.readinggardenkotlin.modules.auth.controller

data class CreateUserRequest(
    val user_email: String,
    val user_password: String,
    val user_fcm: String,
    val user_social_id: String = "",
    val user_social_type: String = "",
)

data class LoginUserRequest(
    val user_email: String,
    val user_password: String,
    val user_fcm: String,
    val user_social_id: String = "",
    val user_social_type: String = "",
)

data class UserEmailRequest(
    val user_email: String,
)

data class UserPasswordAuthRequest(
    val user_email: String,
    val auth_number: String,
)

data class UpdateUserRequest(
    val user_nick: String? = null,
    val user_image: String? = null,
)

data class UpdateUserPasswordRequest(
    val user_email: String? = null,
    val user_password: String,
)

data class RefreshTokenRequest(
    val refresh_token: String,
)
