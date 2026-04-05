package std.nooook.readinggardenkotlin.modules.auth.controller

import jakarta.validation.constraints.Pattern

data class CreateUserRequest(
    @field:Pattern(
        regexp = "^\\w+@[a-zA-Z_]+?\\.[a-zA-Z]{2,3}$",
        message = "must match legacy email format",
    )
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
