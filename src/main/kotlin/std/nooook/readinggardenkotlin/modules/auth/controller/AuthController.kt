package std.nooook.readinggardenkotlin.modules.auth.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.auth.service.AuthService

@RequestMapping("/api/v1/auth")
@RestController
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("", "/")
    fun signup(
        @Valid @RequestBody request: CreateUserRequest,
    ): ResponseEntity<LegacyDataResponse<SignupResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "회원가입 성공",
                data = authService.signup(request),
            ),
        )

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginUserRequest,
    ): LegacyDataResponse<Map<String, String>> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "로그인 성공",
            data = if (request.user_social_id.isBlank()) {
                authService.login(request.user_email, request.user_password, request.user_fcm)
            } else {
                authService.login(
                    email = request.user_email,
                    password = request.user_password,
                    fcmToken = request.user_fcm,
                    socialId = request.user_social_id,
                    socialType = request.user_social_type,
                )
            },
        )

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<Map<String, Any>> {
        authService.logout(principal.userNo.toInt())
        return LegacyDataResponse(
            resp_code = 200,
            resp_msg = "로그아웃 성공",
            data = emptyMap(),
        )
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody request: RefreshTokenRequest,
    ): LegacyDataResponse<String> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "토큰 발급 성공",
            data = authService.refresh(request.refresh_token),
        )

    @DeleteMapping("", "/")
    fun deleteUser(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<Map<String, Any>> {
        authService.deleteUser(principal.userNo.toInt())
        return LegacyDataResponse(
            resp_code = 200,
            resp_msg = "회원 탈퇴 성공",
            data = emptyMap(),
        )
    }

    @PostMapping("/find-password")
    fun findPassword(
        @RequestBody request: UserEmailRequest,
    ): LegacyDataResponse<Map<String, Any>> {
        authService.sendPasswordResetMail(request.user_email)
        return LegacyDataResponse(
            resp_code = 200,
            resp_msg = "메일이 발송되었습니다. 확인해주세요.",
            data = emptyMap(),
        )
    }

    @PostMapping("/find-password/check")
    fun findPasswordCheck(
        @RequestBody request: UserPasswordAuthRequest,
    ): LegacyDataResponse<Map<String, Any>> {
        authService.checkPasswordResetAuth(request.user_email, request.auth_number)
        return LegacyDataResponse(
            resp_code = 200,
            resp_msg = "인증 성공",
            data = emptyMap(),
        )
    }

    @PutMapping("/find-password/update-password")
    fun updatePasswordWithoutToken(
        @RequestBody request: UpdateUserPasswordRequest,
    ): LegacyHttpResponse {
        authService.updatePasswordWithoutToken(request.user_email.orEmpty(), request.user_password)
        return LegacyHttpResponse(
            resp_code = 200,
            resp_msg = "비밀번호 변경 성공",
        )
    }

    @PutMapping("/update-password")
    fun updatePassword(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: UpdateUserPasswordRequest,
    ): LegacyHttpResponse {
        authService.updatePassword(principal.userNo.toInt(), request.user_password)
        return LegacyHttpResponse(
            resp_code = 200,
            resp_msg = "비밀번호 변경 성공",
        )
    }

    @GetMapping("", "/")
    fun getProfile(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<UserProfileResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "조회 성공",
            data = authService.getProfile(principal.userNo.toInt()),
        )

    @PutMapping("", "/")
    fun updateProfile(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: UpdateUserRequest,
    ): LegacyDataResponse<UserSummaryResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "프로필 변경 성공",
            data = authService.updateProfile(
                userNo = principal.userNo.toInt(),
                userNick = request.user_nick,
                userImage = request.user_image,
            ),
        )
}
