package std.nooook.readinggardenkotlin.modules.auth.controller

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern

@Schema(description = "회원가입 요청")
data class CreateUserRequest(
    @field:Schema(description = "로그인에 사용할 이메일. 소셜 로그인(애플 이메일 숨기기 등)에서는 빈 문자열 허용.", example = "user@example.com")
    @field:Pattern(
        regexp = "^$|^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$",
        message = "must match legacy email format",
    )
    val user_email: String,
    @field:Schema(description = "사용자 비밀번호", example = "password1234")
    val user_password: String,
    @field:Schema(description = "기기 FCM 토큰", example = "fcm-token-value")
    val user_fcm: String,
    @field:Schema(description = "소셜 로그인 사용 시 전달하는 소셜 식별자, 일반 회원가입이면 빈 문자열", example = "")
    val user_social_id: String = "",
    @field:Schema(description = "소셜 로그인 타입, 일반 회원가입이면 빈 문자열", example = "")
    val user_social_type: String = "",
)

@Schema(description = "로그인 요청")
data class LoginUserRequest(
    @field:Schema(description = "로그인 이메일", example = "user@example.com")
    val user_email: String,
    @field:Schema(description = "로그인 비밀번호", example = "password1234")
    val user_password: String,
    @field:Schema(description = "기기 FCM 토큰", example = "fcm-token-value")
    val user_fcm: String,
    @field:Schema(description = "소셜 로그인 식별자, 일반 로그인은 빈 문자열", example = "")
    val user_social_id: String = "",
    @field:Schema(description = "소셜 로그인 타입, 일반 로그인은 빈 문자열", example = "")
    val user_social_type: String = "",
)

@Schema(description = "이메일 기반 비밀번호 재설정 요청")
data class UserEmailRequest(
    @field:Schema(description = "비밀번호 재설정 메일을 받을 이메일", example = "user@example.com")
    val user_email: String,
)

@Schema(description = "비밀번호 재설정 인증번호 확인 요청")
data class UserPasswordAuthRequest(
    @field:Schema(description = "인증 대상 이메일", example = "user@example.com")
    val user_email: String,
    @field:Schema(description = "메일로 받은 인증번호", example = "123456")
    val auth_number: String,
)

@Schema(description = "프로필 수정 요청")
data class UpdateUserRequest(
    @field:Schema(description = "수정할 닉네임, 미수정 시 null", example = "새닉네임", nullable = true)
    val user_nick: String? = null,
    @field:Schema(description = "수정할 프로필 이미지 이름 또는 경로, 미수정 시 null", example = "rose", nullable = true)
    val user_image: String? = null,
    @field:Schema(description = "수정할 기기 FCM 토큰, 미수정 시 null", example = "fcm-token-value", nullable = true)
    val user_fcm: String? = null,
)

@Schema(description = "비밀번호 수정 요청")
data class UpdateUserPasswordRequest(
    @field:Schema(description = "토큰 없이 비밀번호를 바꿀 때 사용하는 이메일", example = "user@example.com", nullable = true)
    val user_email: String? = null,
    @field:Schema(description = "새 비밀번호", example = "newPassword1234")
    val user_password: String,
)

@Schema(description = "리프레시 토큰 재발급 요청")
data class RefreshTokenRequest(
    @field:Schema(description = "발급받은 refresh token", example = "refresh-token-value")
    val refresh_token: String,
)
