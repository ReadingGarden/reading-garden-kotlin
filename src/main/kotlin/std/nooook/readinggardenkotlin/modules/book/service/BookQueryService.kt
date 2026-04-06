package std.nooook.readinggardenkotlin.modules.book.service

import jakarta.transaction.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadMemoItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadHistoryItemResponse
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class BookQueryService(
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val gardenRepository: GardenRepository,
    private val memoRepository: MemoRepository,
) {
    companion object {
        private val LEGACY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }

    @Transactional
    fun checkDuplication(
        userNo: Int,
        isbn: String,
    ): String {
        if (bookRepository.findByBookIsbnAndUserNo(isbn, userNo) != null) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "책 중복")
        }
        return "책 등록 가능"
    }

    @Transactional
    fun getBookStatus(
        userNo: Int,
        gardenNo: Int?,
        status: Int?,
        page: Int,
        pageSize: Int,
    ): BookStatusResponse {
        val books = when {
            gardenNo != null -> bookRepository.findAllByUserNoAndGardenNo(userNo, gardenNo)
            else -> bookRepository.findAllByUserNo(userNo)
        }
            .asSequence()
            .filter {
                when (status) {
                    null -> true
                    3 -> it.bookStatus == 0 || it.bookStatus == 1
                    else -> it.bookStatus == status
                }
            }
            .toList()

        val totalItems = books.size
        val maxPage = (totalItems + pageSize - 1) / pageSize
        val fromIndex = (page - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, totalItems)
        val pageBooks = if (fromIndex >= totalItems) emptyList() else books.subList(fromIndex, toIndex)

        return BookStatusResponse(
            current_page = page,
            max_page = maxPage,
            total_items = totalItems,
            page_size = pageSize,
            list = pageBooks.map { book ->
                val bookNo = checkNotNull(book.bookNo) { "Book id is required" }
                val latestRead = bookReadRepository.findTopByBookNoOrderByCreatedAtDesc(bookNo)
                val percent = latestRead?.let { calculatePercent(it.bookCurrentPage, book.bookPage) } ?: 0.0

                BookStatusItemResponse(
                    book_no = bookNo,
                    book_title = book.bookTitle,
                    book_author = book.bookAuthor,
                    book_publisher = book.bookPublisher,
                    book_info = book.bookInfo,
                    book_image_url = book.bookImageUrl,
                    book_tree = book.bookTree,
                    book_status = book.bookStatus,
                    percent = percent,
                    book_page = book.bookPage,
                    garden_no = book.gardenNo,
                )
            },
        )
    }

    @Transactional
    fun getBookRead(bookNo: Int): BookReadDetailResponse {
        val book = bookRepository.findById(bookNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.") }
        val gardenNo = book.gardenNo
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")
        gardenRepository.findById(gardenNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.") }
        val latestRead = bookReadRepository.findTopByBookNoOrderByCreatedAtDesc(bookNo)
        val reads = bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(bookNo)
        val currentPage = latestRead?.bookCurrentPage ?: 0
        val percent = latestRead?.let { calculatePercent(it.bookCurrentPage, book.bookPage) } ?: 0.0
        val memoList = memoRepository.findAllByBookNoOrderByMemoLikeDescMemoCreatedAtDesc(bookNo)
            .map { memo ->
                val memoId = checkNotNull(memo.id) { "Memo id is required" }
                BookReadMemoItemResponse(
                    id = memoId,
                    memo_content = memo.memoContent,
                    memo_like = memo.memoLike,
                    memo_created_at = formatDateTime(memo.memoCreatedAt),
                )
            }

        return BookReadDetailResponse(
            user_no = book.userNo,
            book_title = book.bookTitle,
            book_author = book.bookAuthor,
            book_publisher = book.bookPublisher,
            book_info = book.bookInfo,
            book_image_url = book.bookImageUrl,
            book_tree = book.bookTree,
            book_status = book.bookStatus,
            book_page = book.bookPage,
            garden_no = book.gardenNo,
            book_current_page = currentPage,
            percent = percent,
            book_read_list = reads.map { read ->
                val readId = checkNotNull(read.id) { "Book read id is required" }
                BookReadHistoryItemResponse(
                    id = readId,
                    book_current_page = read.bookCurrentPage,
                    book_start_date = read.bookStartDate?.let(::formatDateTime),
                    book_end_date = read.bookEndDate?.let(::formatDateTime),
                    created_ad = formatDateTime(read.createdAt),
                )
            },
            memo_list = memoList,
        )
    }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(LEGACY_DATE_TIME_FORMATTER)

    private fun calculatePercent(bookCurrentPage: Int, bookPage: Int): Double {
        if (bookPage == 0) {
            throw ArithmeticException("/ by zero")
        }
        return bookCurrentPage.toDouble() / bookPage * 100
    }
}
