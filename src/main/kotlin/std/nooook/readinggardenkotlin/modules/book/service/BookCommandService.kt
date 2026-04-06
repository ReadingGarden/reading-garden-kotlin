package std.nooook.readinggardenkotlin.modules.book.service

import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookRequest
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookResponse
import std.nooook.readinggardenkotlin.modules.book.controller.UpdateBookRequest
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository

@Service
class BookCommandService(
    private val bookRepository: BookRepository,
    private val gardenRepository: GardenRepository,
) {
    @Transactional
    fun createBook(
        userNo: Int,
        request: CreateBookRequest,
    ): CreateBookResponse {
        if (request.garden_no == null) {
            if (bookRepository.findAll().count { it.gardenNo == null } >= MAX_GARDEN_BOOK_COUNT) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "책 생성 개수 초과")
            }
        } else {
            val gardenNo = request.garden_no
            if (!gardenRepository.existsById(gardenNo)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 가든이 없습니다.")
            }
            if (bookRepository.countByGardenNo(gardenNo) >= MAX_GARDEN_BOOK_COUNT) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "책 생성 개수 초과")
            }
        }

        val saved = bookRepository.save(
            BookEntity(
                gardenNo = request.garden_no,
                bookTitle = request.book_title,
                bookAuthor = request.book_author,
                bookPublisher = request.book_publisher,
                bookStatus = request.book_status,
                userNo = userNo,
                bookPage = request.book_page,
                bookIsbn = request.book_isbn,
                bookTree = request.book_tree,
                bookImageUrl = request.book_image_url,
                bookInfo = request.book_info,
            ),
        )

        return CreateBookResponse(
            book_no = checkNotNull(saved.bookNo) { "Book id was not generated" },
        )
    }

    @Transactional
    fun updateBook(
        userNo: Int,
        bookNo: Int,
        request: UpdateBookRequest,
    ): String {
        val book = bookRepository.findByBookNoAndUserNo(bookNo, userNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")

        request.garden_no?.let { targetGardenNo ->
            if (targetGardenNo != book.gardenNo && bookRepository.countByGardenNo(targetGardenNo) >= MAX_GARDEN_BOOK_COUNT) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "가든 옮기기 불가")
            }
            book.gardenNo = targetGardenNo
        }
        request.book_tree?.let { book.bookTree = it }
        request.book_status?.let { book.bookStatus = it }
        bookRepository.save(book)
        return "책 수정 성공"
    }

    companion object {
        private const val MAX_GARDEN_BOOK_COUNT = 30L
    }
}
