package std.nooook.readinggardenkotlin.modules.memo.controller

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "메모 생성 성공 응답")
data class CreateMemoResponse(
    @field:Schema(description = "생성된 메모 id", example = "1")
    val id: Int,
)

@Schema(description = "메모 목록 항목")
data class MemoListItemResponse(
    @field:Schema(description = "메모 id", example = "1")
    val id: Int,
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Int,
    @field:Schema(description = "책 제목", example = "클린 코드")
    val book_title: String,
    @field:Schema(description = "저자", example = "저자")
    val book_author: String,
    @field:Schema(description = "책 이미지 URL", example = "https://example.com/book.jpg", nullable = true)
    val book_image_url: String?,
    @field:Schema(description = "메모 내용", example = "좋았던 문장")
    val memo_content: String,
    @field:Schema(description = "좋아요 여부", example = "true")
    val memo_like: Boolean,
    @field:Schema(description = "메모 이미지 URL", example = "https://example.com/memo.jpg", nullable = true)
    val image_url: String?,
    @field:Schema(description = "메모 작성 시각", example = "2026-04-09T16:30:00")
    val memo_created_at: String,
)

@Schema(description = "메모 목록 응답")
data class MemoListResponse(
    @field:Schema(description = "현재 페이지", example = "1")
    val current_page: Int,
    @field:Schema(description = "최대 페이지", example = "3")
    val max_page: Int,
    @field:Schema(description = "전체 메모 수", example = "20")
    val total: Long,
    @field:Schema(description = "페이지 크기", example = "10")
    val page_size: Int,
    @field:Schema(description = "현재 페이지 메모 목록")
    val list: List<MemoListItemResponse>,
)

@Schema(description = "메모 상세 응답")
data class MemoDetailResponse(
    @field:Schema(description = "메모 id", example = "1")
    val id: Int,
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Int,
    @field:Schema(description = "책 제목", example = "상세 메모 책")
    val book_title: String,
    @field:Schema(description = "저자", example = "저자")
    val book_author: String,
    @field:Schema(description = "출판사", example = "출판사")
    val book_publisher: String,
    @field:Schema(description = "책 소개", example = "메모 상세용 책 소개")
    val book_info: String,
    @field:Schema(description = "메모 내용", example = "상세 메모 내용")
    val memo_content: String,
    @field:Schema(description = "메모 이미지 URL", example = "https://example.com/memo.jpg", nullable = true)
    val image_url: String?,
    @field:Schema(description = "메모 작성 시각", example = "2026-04-09T16:30:00")
    val memo_created_at: String,
)
