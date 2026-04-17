package std.nooook.readinggardenkotlin.modules.auth.controller

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "회원가입 성공 응답 데이터")
data class SignupResponse(
    @field:Schema(description = "로그인에 사용할 access token", example = "fixture-access-token")
    val access_token: String,
    @field:Schema(description = "토큰 재발급용 refresh token", example = "fixture-refresh-token")
    val refresh_token: String,
    @field:Schema(description = "생성된 사용자 닉네임", example = "임의닉네임")
    val user_nick: String,
)

@Schema(description = "로그인 성공 응답 데이터")
data class LoginResponseData(
    @field:Schema(description = "로그인에 사용할 access token", example = "fixture-access-token")
    val access_token: String,
    @field:Schema(description = "토큰 재발급용 refresh token", example = "fixture-refresh-token")
    val refresh_token: String,
)

@Schema(description = "빈 객체 응답 데이터")
class EmptyResponseData

@Schema(description = "내 프로필 응답 데이터")
data class UserProfileResponse(
    @field:Schema(description = "사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "닉네임", example = "임의닉네임")
    val user_nick: String,
    @field:Schema(description = "이메일", example = "user@example.com")
    val user_email: String,
    @field:Schema(description = "소셜 로그인 타입, 일반 계정은 빈 문자열", example = "")
    val user_social_type: String,
    @field:Schema(description = "프로필 이미지", example = "데이지")
    val user_image: String,
    @field:Schema(description = "가입 시각", example = "2026-04-09T16:00:00")
    val user_created_at: LocalDateTime,
    @field:Schema(description = "참여 중인 가든 수", example = "1")
    val garden_count: Long,
    @field:Schema(description = "완독 또는 진행 중 독서 수", example = "0")
    val read_book_count: Long,
    @field:Schema(description = "좋아요 받은 책/메모 관련 집계", example = "0")
    val like_book_count: Long,
)

@Schema(description = "프로필 수정 후 요약 응답 데이터")
data class UserSummaryResponse(
    @field:Schema(description = "사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "닉네임", example = "임의닉네임")
    val user_nick: String,
    @field:Schema(description = "이메일", example = "user@example.com")
    val user_email: String,
    @field:Schema(description = "프로필 이미지", example = "데이지")
    val user_image: String,
    @field:Schema(description = "현재 저장된 FCM 토큰", example = "fcm-token-value")
    val user_fcm: String,
    @field:Schema(description = "소셜 로그인 식별자", example = "")
    val user_social_id: String,
    @field:Schema(description = "소셜 로그인 타입", example = "")
    val user_social_type: String,
    @field:Schema(description = "가입 시각", example = "2026-04-09T16:00:00")
    val user_created_at: LocalDateTime,
)

@Schema(name = "SignupLegacyDataResponse", description = "회원가입 성공 레거시 envelope")
data class SignupLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "201")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "회원가입 성공")
    val resp_msg: String,
    @field:Schema(description = "회원가입 결과 데이터")
    val data: SignupResponse,
)

@Schema(name = "LoginLegacyDataResponse", description = "로그인 성공 레거시 envelope")
data class LoginLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "로그인 성공")
    val resp_msg: String,
    @field:Schema(description = "로그인 토큰 데이터")
    val data: LoginResponseData,
)

@Schema(name = "EmptyLegacyDataResponse", description = "빈 객체 데이터를 담는 레거시 envelope")
data class EmptyLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "처리 성공")
    val resp_msg: String,
    @field:Schema(description = "빈 객체 데이터")
    val data: EmptyResponseData,
)

@Schema(name = "TokenRefreshLegacyDataResponse", description = "토큰 재발급 성공 레거시 envelope")
data class TokenRefreshLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "토큰 발급 성공")
    val resp_msg: String,
    @field:Schema(description = "새 access token", example = "fixture-access-token")
    val data: String,
)

@Schema(name = "UserProfileLegacyDataResponse", description = "내 프로필 조회 성공 레거시 envelope")
data class UserProfileLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "조회 성공")
    val resp_msg: String,
    @field:Schema(description = "프로필 조회 데이터")
    val data: UserProfileResponse,
)

@Schema(name = "UserSummaryLegacyDataResponse", description = "프로필 수정 성공 레거시 envelope")
data class UserSummaryLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "프로필 변경 성공")
    val resp_msg: String,
    @field:Schema(description = "프로필 수정 결과 데이터")
    val data: UserSummaryResponse,
)
