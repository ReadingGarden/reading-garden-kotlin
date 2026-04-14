package std.nooook.readinggardenkotlin.modules.memo.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListItemResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListResponse
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class MemoService(
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val bookRepository: BookRepository,
) {
    fun getMemoList(
        userNo: Int,
        page: Int = 1,
        pageSize: Int = 10,
    ): MemoListResponse {
        val memoPage = memoRepository.findAllByUserNoJoinBookOrderByMemoLikeDescMemoCreatedAtDesc(
            userNo = userNo,
            pageable = PageRequest.of(page - 1, pageSize),
        )

        val memoIds = memoPage.content.mapNotNull { it.id }
        val bookNos = memoPage.content.map { it.bookNo }.distinct()
        val booksByNo = bookRepository.findAllById(bookNos).associateBy { it.bookNo ?: -1 }
        val imagesByMemoNo = memoImageRepository.findAllByMemoNoIn(memoIds).associateBy { it.memoNo }

        return MemoListResponse(
            current_page = page,
            max_page = memoPage.totalPages,
            total = memoPage.totalElements,
            page_size = pageSize,
            list = memoPage.content.map { memo ->
                val memoId = checkNotNull(memo.id) { "Memo id is required" }
                val book = checkNotNull(booksByNo[memo.bookNo]) { "Book ${memo.bookNo} is missing for memo $memoId" }
                val imageUrl = imagesByMemoNo[memoId]?.imageUrl

                MemoListItemResponse(
                    id = memoId,
                    book_no = memo.bookNo,
                    book_title = book.bookTitle,
                    book_author = book.bookAuthor,
                    book_image_url = book.bookImageUrl,
                    memo_content = memo.memoContent,
                    memo_like = memo.memoLike,
                    image_url = imageUrl,
                    memo_created_at = memo.memoCreatedAt.toString(),
                )
            },
        )
    }
}
