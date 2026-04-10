package std.nooook.readinggardenkotlin.modules.book.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.service.BookCommandService
import std.nooook.readinggardenkotlin.modules.book.service.BookImageService
import std.nooook.readinggardenkotlin.modules.book.service.BookQueryService
import std.nooook.readinggardenkotlin.modules.book.service.BookReadService
import std.nooook.readinggardenkotlin.modules.book.service.BookService
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RequestMapping("/api/v1/book")
@RestController
@Tag(name = "Book", description = "책 검색, 등록, 상태 조회, 독서 기록, 책 이미지")
class BookController(
    private val bookService: BookService,
    private val bookQueryService: BookQueryService,
    private val bookCommandService: BookCommandService,
    private val bookReadService: BookReadService,
    private val bookImageService: BookImageService,
) {
    @GetMapping("", "/")
    @Operation(summary = "책 중복 확인", description = "현재 사용자 기준으로 같은 ISBN의 책이 이미 등록되었는지 확인합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun checkBookDuplication(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "확인할 ISBN13", example = "9788937462788")
        @RequestParam isbn: String,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookQueryService.checkDuplication(principal.userNo.toInt(), isbn),
        )

    @GetMapping("/search")
    @Operation(summary = "책 검색", description = "알라딘 검색 결과를 레거시 응답 형식으로 반환합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "책 검색 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = BookSearchPayloadDocument::class),
                        examples = [ExampleObject(value = OpenApiExamples.BOOK_SEARCH_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun searchBooks(
        @Parameter(description = "검색어", example = "클린 코드")
        @RequestParam query: String,
        @Parameter(description = "검색 시작 index", example = "1")
        @RequestParam(required = false, defaultValue = "1") start: Int,
        @Parameter(description = "최대 조회 개수", example = "100")
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
    @Operation(summary = "ISBN 검색", description = "ISBN13으로 책을 검색합니다. 알라딘 응답 payload를 그대로 노출합니다.")
    fun searchBookByIsbn(
        @Parameter(description = "ISBN13", example = "9788937462788")
        @RequestParam query: String,
    ): LegacyDataResponse<BookLookupResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 검색(ISBN) 성공",
            data = bookService.searchBookByIsbn(query),
        )

    @GetMapping("/detail-isbn")
    @Operation(summary = "책 상세 조회", description = "ISBN13 기준으로 책 상세 정보를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "책 상세 조회 성공",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.BOOK_DETAIL_SUCCESS)])],
            ),
        ],
    )
    fun getBookDetailByIsbn(
        @Parameter(description = "ISBN13", example = "9788937462788")
        @RequestParam query: String,
    ): LegacyDataResponse<BookDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "책 상세 조회 성공",
            data = bookService.getBookDetailByIsbn(query),
        )

    @PostMapping("", "/")
    @Operation(
        summary = "책 등록",
        description = "현재 사용자 계정에 책을 등록합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateBookRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
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
    @Operation(summary = "책 삭제", description = "`book_no`에 해당하는 책을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun deleteBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "삭제할 책 번호", example = "1")
        @RequestParam(name = "book_no") bookNo: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookCommandService.deleteBook(principal.userNo.toInt(), bookNo),
        )

    @GetMapping("/status")
    @Operation(summary = "책 상태 목록 조회", description = "가든 번호, 상태 코드, 페이지네이션 조건으로 책 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun getBookStatus(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "가든 번호 필터", example = "10")
        @RequestParam(name = "garden_no", required = false) gardenNo: Int?,
        @Parameter(description = "상태 코드 필터", example = "1")
        @RequestParam(required = false) status: Int?,
        @Parameter(description = "페이지 번호", example = "1")
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @Parameter(description = "페이지 크기", example = "10")
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
    @Operation(summary = "독서 기록 상세 조회", description = "`book_no` 기준으로 독서 이력과 연결 메모를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun getBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "조회할 책 번호", example = "1")
        @RequestParam(name = "book_no") bookNo: Int,
    ): LegacyDataResponse<BookReadDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "독서 기록 조회 성공",
            data = bookQueryService.getBookRead(bookNo),
        )

    @PostMapping("/read")
    @Operation(
        summary = "독서 기록 생성",
        description = "책의 현재 페이지와 읽기 시작/종료 시각을 기록합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateReadRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
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
    @Operation(
        summary = "독서 기록 수정",
        description = "`id`에 해당하는 독서 기록의 시작/종료 시각을 수정합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateReadRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    fun updateBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "수정할 독서 기록 id", example = "1")
        @RequestParam id: Int,
        @RequestBody request: UpdateReadRequest,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookReadService.updateRead(id, request),
        )

    @DeleteMapping("/read")
    @Operation(summary = "독서 기록 삭제", description = "`id`에 해당하는 독서 기록을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun deleteBookRead(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "삭제할 독서 기록 id", example = "1")
        @RequestParam id: Int,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            bookReadService.deleteRead(id),
        )

    @PostMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "책 이미지 업로드",
        description = "`multipart/form-data`로 책 이미지를 업로드합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "multipart/form-data", schema = Schema(implementation = UploadBookImageRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "업로드 성공",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.CREATED_EMPTY_SUCCESS)])],
            ),
            ApiResponse(
                responseCode = "400",
                description = "multipart 요청 형식 오류",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.BAD_REQUEST)])],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(mediaType = "application/json", examples = [ExampleObject(value = OpenApiExamples.UNAUTHORIZED)])],
            ),
        ],
    )
    fun uploadBookImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "이미지를 연결할 책 번호", example = "1")
        @RequestParam(name = "book_no") bookNo: Int,
        @Parameter(description = "업로드할 이미지 파일", required = true)
        @RequestParam(name = "file") file: MultipartFile,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = bookImageService.uploadBookImage(bookNo, file),
            ),
        )

    @DeleteMapping("/image")
    @Operation(summary = "책 이미지 삭제", description = "`book_no`에 연결된 사용자 업로드 이미지를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun deleteBookImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "이미지를 삭제할 책 번호", example = "1")
        @RequestParam(name = "book_no") bookNo: Int,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = bookImageService.deleteBookImage(bookNo),
            ),
        )

    @PutMapping("", "/")
    @Operation(
        summary = "책 수정",
        description = "가든 이동, 분류 변경, 상태 변경 등 책 정보를 수정합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateBookRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    fun updateBook(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "수정할 책 번호", example = "1")
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
