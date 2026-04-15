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
        userId: Long,
        page: Int = 1,
        pageSize: Int = 10,
    ): MemoListResponse {
        val memoPage = memoRepository.findAllByUserIdJoinBookOrderByIsLikedDescCreatedAtDesc(
            userId = userId,
            pageable = PageRequest.of(page - 1, pageSize),
        )

        val memoIds = memoPage.content.map { it.id }
        val bookIds = memoPage.content.map { it.book.id }.distinct()
        val booksById = bookRepository.findAllById(bookIds).associateBy { it.id }
        val imagesByMemoId = memoImageRepository.findAllByMemoIdIn(memoIds).associateBy { it.memo.id }

        return MemoListResponse(
            current_page = page,
            max_page = memoPage.totalPages,
            total = memoPage.totalElements,
            page_size = pageSize,
            list = memoPage.content.map { memo ->
                val book = checkNotNull(booksById[memo.book.id]) { "Book ${memo.book.id} is missing for memo ${memo.id}" }
                val imageUrl = imagesByMemoId[memo.id]?.url

                MemoListItemResponse(
                    id = memo.id,
                    book_no = memo.book.id,
                    book_title = book.title,
                    book_author = book.author,
                    book_image_url = book.imageUrl,
                    memo_content = memo.content,
                    memo_like = memo.isLiked,
                    image_url = imageUrl,
                    memo_created_at = memo.createdAt.toString(),
                )
            },
        )
    }
}
