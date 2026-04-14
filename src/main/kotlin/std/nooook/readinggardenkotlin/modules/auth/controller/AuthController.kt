package std.nooook.readinggardenkotlin.modules.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.auth.service.AuthService
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RequestMapping("/api/v1/auth")
@RestController
@Tag(name = "Auth", description = "회원가입, 로그인, 프로필, 비밀번호 재설정")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("")
    @Operation(
        summary = "회원가입",
        description = "레거시 앱이 사용하는 회원가입 API입니다. `user_email`, `user_password`, `user_fcm`를 JSON body로 전달합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            description = "회원가입 요청 본문",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateUserRequest::class),
                ),
            ],
        ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "회원가입 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        examples = [ExampleObject(name = "signup-success", value = OpenApiExamples.AUTH_SIGNUP_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "요청 본문 검증 실패",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.BAD_REQUEST)])],
            ),
        ],
    )
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
    @Operation(
        summary = "로그인",
        description = "이메일/비밀번호 또는 소셜 식별자 조합으로 로그인합니다. 일반 로그인에서는 `user_social_id`, `user_social_type`을 빈 문자열로 전달합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            description = "로그인 요청 본문",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = LoginUserRequest::class))],
        ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.AUTH_LOGIN_SUCCESS)])],
            ),
            ApiResponse(
                responseCode = "400",
                description = "로그인 요청 형식 오류",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.BAD_REQUEST)])],
            ),
        ],
    )
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
    @Operation(summary = "로그아웃", description = "현재 access token을 기준으로 로그아웃합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.UNAUTHORIZED)])],
            ),
        ],
    )
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
    @Operation(
        summary = "토큰 재발급",
        description = "refresh token으로 access token을 다시 발급합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = RefreshTokenRequest::class))],
        ),
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "토큰 발급 성공"),
            ApiResponse(responseCode = "400", description = "리프레시 토큰 형식 오류"),
        ],
    )
    fun refresh(
        @RequestBody request: RefreshTokenRequest,
    ): LegacyDataResponse<String> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "토큰 발급 성공",
            data = authService.refresh(request.refresh_token),
        )

    @DeleteMapping("")
    @Operation(summary = "회원 탈퇴", description = "현재 인증된 사용자를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.UNAUTHORIZED)])],
            ),
        ],
    )
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
    @Operation(
        summary = "비밀번호 찾기 메일 발송",
        description = "입력한 이메일로 비밀번호 재설정 인증 메일을 발송합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UserEmailRequest::class))],
        ),
    )
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
    @Operation(
        summary = "비밀번호 재설정 인증번호 확인",
        description = "메일로 받은 `auth_number`가 유효한지 확인합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UserPasswordAuthRequest::class))],
        ),
    )
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
    @Operation(
        summary = "비인증 비밀번호 변경",
        description = "비밀번호 재설정 인증을 마친 뒤 이메일 기준으로 비밀번호를 변경합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateUserPasswordRequest::class))],
        ),
    )
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
    @Operation(
        summary = "내 비밀번호 변경",
        description = "현재 로그인한 사용자의 비밀번호를 변경합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateUserPasswordRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
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

    @GetMapping("")
    @Operation(summary = "내 프로필 조회", description = "현재 인증된 사용자의 프로필과 집계 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "프로필 조회 성공",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.AUTH_PROFILE_SUCCESS)])],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.UNAUTHORIZED)])],
            ),
        ],
    )
    fun getProfile(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
    ): LegacyDataResponse<UserProfileResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "조회 성공",
            data = authService.getProfile(principal.userNo.toInt()),
        )

    @PutMapping("")
    @Operation(
        summary = "프로필 수정",
        description = "닉네임과 프로필 이미지를 수정합니다. 변경하지 않을 필드는 null로 보낼 수 있습니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateUserRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
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
