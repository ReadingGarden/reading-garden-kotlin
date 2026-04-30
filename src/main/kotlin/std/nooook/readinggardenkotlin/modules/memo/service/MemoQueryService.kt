package std.nooook.readinggardenkotlin.modules.memo.service

import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoDetailResponse
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class MemoQueryService(
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val bookRepository: BookRepository,
) {
    companion object {
        private val LEGACY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }

    @Transactional(readOnly = true)
    fun getMemoDetail(
        userId: Long,
        id: Long,
    ): MemoDetailResponse {
        val memo = memoRepository.findByIdAndUserId(id, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val book = bookRepository.findByIdAndUserId(memo.book.id, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val imageUrl = memoImageRepository.findFirstByMemoIdOrderByCreatedAtDesc(memo.id)?.url

        return MemoDetailResponse(
            id = memo.id,
            book_no = memo.book.id,
            book_title = book.title,
            book_author = book.author,
            book_publisher = book.publisher,
            book_info = book.info,
            memo_content = memo.content,
            image_url = imageUrl,
            memo_created_at = memo.createdAt.format(LEGACY_DATE_TIME_FORMATTER),
        )
    }
}
