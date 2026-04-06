package std.nooook.readinggardenkotlin.modules.book.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.service.BookCommandService
import std.nooook.readinggardenkotlin.modules.book.service.BookImageService
import std.nooook.readinggardenkotlin.modules.book.service.BookQueryService
import std.nooook.readinggardenkotlin.modules.book.service.BookReadService
import std.nooook.readinggardenkotlin.modules.book.service.BookService

@RequestMapping("/api/v1/book")
@RestController
class BookController(
    private val bookService: BookService,
    private val bookQueryService: BookQueryService,
    private val bookCommandService: BookCommandService,
    private val bookReadService: BookReadService,
    private val bookImageService: BookImageService,
) {
    @GetMapping("", "/")
    fun checkBookDuplication(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam isbn: String,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookQueryService.checkDuplication(principal.userNo.toInt(), isbn),
        )

    @GetMapping("/search")
    fun searchBooks(
        @RequestParam query: String,
        @RequestParam(required = false, defaultValue = "1") start: Int,
        @RequestParam(required = false, defaultValue = "100") maxResults: Int,
    ): LegacyDataResponse<BookSearchResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 검색 성공",
            data = bookService.searchBooks(
                query = query,
                start = start,
                maxResults = maxResults,
            ),
        )

    @GetMapping("/search-isbn")
    fun searchBookByIsbn(
        @RequestParam query: String,
    ): LegacyDataResponse<BookLookupResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 검색(ISBN) 성공",
            data = bookService.searchBookByIsbn(query),
        )

    @GetMapping("/detail-isbn")
    fun getBookDetailByIsbn(
        @RequestParam query: String,
    ): LegacyDataResponse<BookDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 상세 조회 성공",
            data = bookService.getBookDetailByIsbn(query),
        )

    @PostMapping("", "/")
    fun createBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateBookRequest,
    ): ResponseEntity<LegacyDataResponse<CreateBookResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "책 등록 성공",
                data = bookCommandService.createBook(principal.userNo.toInt(), request),
            ),
        )

    @DeleteMapping("", "/")
    fun deleteBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "book_no") bookNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookCommandService.deleteBook(principal.userNo.toInt(), bookNo),
        )

    @GetMapping("/status")
    fun getBookStatus(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "garden_no", required = false) gardenNo: Int?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", required = false, defaultValue = "10") pageSize: Int,
    ): LegacyDataResponse<BookStatusResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 상태 조회 성공",
            data = bookQueryService.getBookStatus(
                userNo = principal.userNo.toInt(),
                gardenNo = gardenNo,
                status = status,
                page = page,
                pageSize = pageSize,
            ),
        )

    @GetMapping("/read")
    fun getBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "book_no") bookNo: Int,
    ): LegacyDataResponse<BookReadDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "독서 기록 조회 성공",
            data = bookQueryService.getBookRead(bookNo),
        )

    @PostMapping("/read")
    fun createBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateReadRequest,
    ): ResponseEntity<LegacyDataResponse<CreateReadResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "책 기록 성공",
                data = bookReadService.createRead(principal.userNo.toInt(), request),
            ),
        )

    @PutMapping("/read")
    fun updateBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
        @RequestBody request: UpdateReadRequest,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookReadService.updateRead(id, request),
        )

    @DeleteMapping("/read")
    fun deleteBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam id: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookReadService.deleteRead(id),
        )

    @PostMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadBookImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "book_no") bookNo: Int,
        @RequestParam(name = "file") file: MultipartFile,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = bookImageService.uploadBookImage(bookNo, file),
            ),
        )

    @DeleteMapping("/image")
    fun deleteBookImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "book_no") bookNo: Int,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = bookImageService.deleteBookImage(bookNo),
            ),
        )

    @PutMapping("", "/")
    fun updateBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestParam(name = "book_no") bookNo: Int,
        @RequestBody request: UpdateBookRequest,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookCommandService.updateBook(
                userNo = principal.userNo.toInt(),
                bookNo = bookNo,
                request = request,
            ),
        )
}
