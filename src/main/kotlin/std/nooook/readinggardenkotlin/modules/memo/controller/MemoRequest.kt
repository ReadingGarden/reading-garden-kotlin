package std.nooook.readinggardenkotlin.modules.memo.controller

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "메모 생성 요청")
data class CreateMemoRequest(
    @field:Schema(description = "연결할 책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "메모 내용", example = "좋았던 문장")
    val memo_content: String,
)

@Schema(description = "메모 수정 요청")
data class UpdateMemoRequest(
    @field:Schema(description = "연결된 책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "수정할 메모 내용", example = "수정된 메모")
    val memo_content: String,
)

@Schema(description = "메모 이미지 업로드용 multipart 요청")
data class UploadMemoImageRequest(
    @field:Schema(description = "이미지를 연결할 메모 id", example = "1")
    val id: Long,
    @field:Schema(description = "업로드할 이미지 파일", type = "string", format = "binary")
    val file: String,
)
