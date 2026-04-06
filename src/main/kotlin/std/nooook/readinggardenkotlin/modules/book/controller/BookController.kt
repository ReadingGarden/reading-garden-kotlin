package std.nooook.readinggardenkotlin.modules.book.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.service.BookCommandService
import std.nooook.readinggardenkotlin.modules.book.service.BookQueryService
import std.nooook.readinggardenkotlin.modules.book.service.BookService

@RequestMapping("/api/v1/book")
@RestController
class BookController(
    private val bookService: BookService,
    private val bookQueryService: BookQueryService,
    private val bookCommandService: BookCommandService,
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
