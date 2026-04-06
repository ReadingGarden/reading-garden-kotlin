package std.nooook.readinggardenkotlin.modules.book.service

import jakarta.transaction.Transactional
import kotlin.math.ceil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusResponse
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository

@Service
class BookQueryService(
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
) {
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
            .sortedByDescending { it.bookNo ?: Int.MIN_VALUE }
            .toList()

        val safePageSize = if (pageSize > 0) pageSize else 10
        val totalItems = books.size
        val maxPage = if (totalItems == 0) 0 else ceil(totalItems.toDouble() / safePageSize).toInt()
        val fromIndex = ((page - 1).coerceAtLeast(0)) * safePageSize
        val toIndex = minOf(fromIndex + safePageSize, totalItems)
        val pageBooks = if (fromIndex >= totalItems) emptyList() else books.subList(fromIndex, toIndex)

        return BookStatusResponse(
            current_page = page,
            max_page = maxPage,
            total_items = totalItems,
            page_size = safePageSize,
            list = pageBooks.map { book ->
                val bookNo = checkNotNull(book.bookNo) { "Book id is required" }
                val latestRead = bookReadRepository.findTopByBookNoOrderByCreatedAtDesc(bookNo)
                val percent = latestRead?.let {
                    if (book.bookPage > 0) {
                        it.bookCurrentPage.toDouble() / book.bookPage * 100
                    } else {
                        0.0
                    }
                } ?: 0.0

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
}
