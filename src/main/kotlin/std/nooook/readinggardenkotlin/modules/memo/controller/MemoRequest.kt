package std.nooook.readinggardenkotlin.modules.memo.controller

data class CreateMemoRequest(
    val book_no: Int,
    val memo_content: String,
)

data class UpdateMemoRequest(
    val book_no: Int,
    val memo_content: String,
)
