package std.nooook.readinggardenkotlin.modules.garden.controller

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "가든 생성 성공 응답")
data class CreateGardenResponse(
    @field:Schema(description = "가든 번호", example = "10")
    val garden_no: Long,
    @field:Schema(description = "가든 제목", example = "새 가든")
    val garden_title: String,
    @field:Schema(description = "가든 소개", example = "소개")
    val garden_info: String,
    @field:Schema(description = "가든 색상", example = "blue")
    val garden_color: String,
)

@Schema(description = "가든 멤버 항목")
data class GardenListMemberResponse(
    @field:Schema(description = "사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "닉네임", example = "임의닉네임")
    val user_nick: String,
    @field:Schema(description = "프로필 이미지", example = "데이지")
    val user_image: String,
    @field:Schema(description = "리더 여부", example = "true")
    val garden_leader: Boolean,
    @field:Schema(description = "가든 가입 시각", example = "2024-01-06T07:00:00")
    val garden_sign_date: String,
)

@Schema(description = "가든 목록 항목")
data class GardenListItemResponse(
    @field:Schema(description = "가든 번호", example = "10")
    val garden_no: Long,
    @field:Schema(description = "가든 제목", example = "메인 가든")
    val garden_title: String,
    @field:Schema(description = "가든 소개", example = "첫 번째")
    val garden_info: String,
    @field:Schema(description = "가든 색상", example = "green")
    val garden_color: String,
    @field:Schema(description = "가든 멤버 수", example = "2")
    val garden_members: Int,
    @field:Schema(description = "가든 내 책 수", example = "1")
    val book_count: Int,
    @field:Schema(description = "가든 생성 시각", example = "2024-01-05T08:30:00")
    val garden_created_at: String,
)

@Schema(description = "가든 상세의 책 항목")
data class GardenDetailBookResponse(
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "ISBN13", example = "9788937462788", nullable = true)
    val book_isbn: String?,
    @field:Schema(description = "책 제목", example = "메인 책")
    val book_title: String,
    @field:Schema(description = "저자", example = "저자")
    val book_author: String,
    @field:Schema(description = "출판사", example = "출판사")
    val book_publisher: String,
    @field:Schema(description = "책 소개", example = "메인 책 소개")
    val book_info: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/main.jpg", nullable = true)
    val book_image_url: String?,
    @field:Schema(description = "분류명", example = "소설", nullable = true)
    val book_tree: String?,
    @field:Schema(description = "독서 상태 코드", example = "1")
    val book_status: Int,
    @field:Schema(description = "진행률", example = "25.0")
    val percent: Double,
    @field:Schema(description = "등록한 사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "전체 페이지 수", example = "100")
    val book_page: Int,
)

@Schema(description = "가든 상세 응답")
data class GardenDetailResponse(
    @field:Schema(description = "가든 번호", example = "10")
    val garden_no: Long,
    @field:Schema(description = "가든 제목", example = "메인 가든")
    val garden_title: String,
    @field:Schema(description = "가든 소개", example = "첫 번째")
    val garden_info: String,
    @field:Schema(description = "가든 색상", example = "green")
    val garden_color: String,
    @field:Schema(description = "생성 시각", example = "2024-01-05T08:30:00")
    val garden_created_at: String,
    @field:Schema(description = "가든에 포함된 책 목록")
    val book_list: List<GardenDetailBookResponse>,
    @field:Schema(description = "가든 멤버 목록")
    val garden_members: List<GardenListMemberResponse>,
)

@Schema(description = "가든 빈 객체 응답 데이터")
class GardenEmptyData

@Schema(name = "GardenListLegacyDataResponse", description = "가든 목록 조회 성공 레거시 envelope")
data class GardenListLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "가든 리스트 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "가든 목록 데이터")
    val data: List<GardenListItemResponse>,
)

@Schema(name = "GardenDetailLegacyDataResponse", description = "가든 상세 조회 성공 레거시 envelope")
data class GardenDetailLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "가든 상세 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "가든 상세 데이터")
    val data: GardenDetailResponse,
)

@Schema(name = "CreateGardenLegacyDataResponse", description = "가든 생성 성공 레거시 envelope")
data class CreateGardenLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "201")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "가든 추가 성공")
    val resp_msg: String,
    @field:Schema(description = "생성된 가든 데이터")
    val data: CreateGardenResponse,
)

@Schema(name = "GardenEmptyLegacyDataResponse", description = "빈 객체 데이터를 담는 가든 레거시 envelope")
data class GardenEmptyLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "가든 수정 성공")
    val resp_msg: String,
    @field:Schema(description = "빈 객체 데이터")
    val data: GardenEmptyData,
)
