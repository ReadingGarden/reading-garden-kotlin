package std.nooook.readinggardenkotlin.modules.book.service

import jakarta.transaction.Transactional
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadRequest
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadResponse
import std.nooook.readinggardenkotlin.modules.book.controller.UpdateReadRequest
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository

@Service
class BookReadService(
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
) {
    @Transactional
    fun createRead(
        userNo: Int,
        request: CreateReadRequest,
    ): CreateReadResponse {
        val book = bookRepository.findByBookNoAndUserNo(request.book_no, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")
        val isFirstRead = bookReadRepository.findAllByBookNoOrderByCreatedAtDesc(request.book_no)
            .none { it.userNo == userNo }
        val now = LocalDateTime.now()
        val currentPage = request.book_current_page
        val isFinalPage = currentPage == book.bookPage
        val resolvedStartDate = when {
            isFirstRead && request.book_start_date == null -> now
            else -> request.book_start_date
        }
        val resolvedEndDate = if (isFinalPage) now else request.book_end_date
        bookReadRepository.save(
            BookReadEntity(
                bookNo = request.book_no,
                bookCurrentPage = currentPage,
                bookStartDate = resolvedStartDate,
                bookEndDate = resolvedEndDate,
                createdAt = now,
                userNo = userNo,
            ),
        )
        when {
            isFinalPage -> {
                book.bookStatus = 1
                bookRepository.save(book)
            }
            isFirstRead && request.book_start_date == null -> {
                book.bookStatus = 0
                bookRepository.save(book)
            }
        }

        return CreateReadResponse(
            book_current_page = currentPage,
            percent = calculatePercent(currentPage, book.bookPage),
        )
    }

    @Transactional
    fun updateRead(
        id: Int,
        request: UpdateReadRequest,
    ): String {
        val read = bookReadRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 기록이 없습니다.") }
        request.book_start_date?.let { read.bookStartDate = it }
        request.book_end_date?.let { read.bookEndDate = it }
        bookReadRepository.save(read)
        return "독서 기록 수정 성공"
    }

    @Transactional
    fun deleteRead(id: Int): String {
        if (!bookReadRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 기록이 없습니다.")
        }
        bookReadRepository.deleteById(id)
        return "책 기록 삭제 성공"
    }

    private fun calculatePercent(
        currentPage: Int,
        bookPage: Int,
    ): Double =
        if (bookPage > 0) {
            currentPage.toDouble() / bookPage * 100
        } else {
            0.0
        }
}
