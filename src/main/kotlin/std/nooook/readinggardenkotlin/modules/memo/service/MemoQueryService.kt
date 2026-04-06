package std.nooook.readinggardenkotlin.modules.memo.service

import jakarta.transaction.Transactional
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

    @Transactional
    fun getMemoDetail(
        userNo: Int,
        id: Int,
    ): MemoDetailResponse {
        val memo = memoRepository.findByIdAndUserNo(id, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val book = bookRepository.findByBookNoAndUserNo(memo.bookNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val imageUrl = memoImageRepository.findFirstByMemoNoOrderByImageCreatedAtDesc(memo.id ?: id)?.imageUrl

        return MemoDetailResponse(
            id = memo.id ?: id,
            book_no = memo.bookNo,
            book_title = book.bookTitle,
            book_author = book.bookAuthor,
            book_publisher = book.bookPublisher,
            book_info = book.bookInfo,
            memo_content = memo.memoContent,
            image_url = imageUrl,
            memo_created_at = memo.memoCreatedAt.format(LEGACY_DATE_TIME_FORMATTER),
        )
    }
}
