package std.nooook.readinggardenkotlin.modules.book

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.controller.BookDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadHistoryItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadMemoItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusResponse
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookRequest
import std.nooook.readinggardenkotlin.modules.book.controller.CreateBookResponse
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadResponse
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadRequest
import std.nooook.readinggardenkotlin.modules.book.service.BookCommandService
import std.nooook.readinggardenkotlin.modules.book.service.BookImageService
import std.nooook.readinggardenkotlin.modules.book.service.BookQueryService
import std.nooook.readinggardenkotlin.modules.book.service.BookReadService
import std.nooook.readinggardenkotlin.modules.book.service.BookService
import org.springframework.web.server.ResponseStatusException

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var bookService: BookService

    @MockitoBean
    private lateinit var bookQueryService: BookQueryService

    @MockitoBean
    private lateinit var bookCommandService: BookCommandService

    @MockitoBean
    private lateinit var bookReadService: BookReadService

    @MockitoBean
    private lateinit var bookImageService: BookImageService

    @Test
    fun `search should return legacy success envelope`() {
        given(bookService.searchBooks("해리포터", 2, 10))
            .willReturn(
                mapOf(
                    "query" to "해리포터",
                    "startIndex" to 2,
                    "itemsPerPage" to 10,
                    "item" to listOf(mapOf("title" to "해리 포터와 마법사의 돌")),
                ),
            )

        mockMvc.perform(
            get("/api/v1/book/search")
                .with(bookAuth())
                .queryParam("query", "해리포터")
                .queryParam("start", "2")
                .queryParam("maxResults", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색 성공"))
            .andExpect(jsonPath("$.data.query").value("해리포터"))
            .andExpect(jsonPath("$.data.item[0].title").value("해리 포터와 마법사의 돌"))
    }

    @Test
    fun `search isbn should return legacy success envelope`() {
        given(bookService.searchBookByIsbn("9788937462788"))
            .willReturn(
                mapOf(
                    "item" to listOf(mapOf("isbn13" to "9788937462788")),
                ),
            )

        mockMvc.perform(
            get("/api/v1/book/search-isbn")
                .with(bookAuth())
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색(ISBN) 성공"))
            .andExpect(jsonPath("$.data.item[0].isbn13").value("9788937462788"))
    }

    @Test
    fun `detail isbn should return legacy success envelope`() {
        given(bookService.getBookDetailByIsbn("9788937462788"))
            .willReturn(
                BookDetailResponse(
                    searchCategoryId = 123,
                    searchCategoryName = "소설",
                    title = "책 제목",
                    author = "저자",
                    description = "소개",
                    isbn13 = "9788937462788",
                    cover = "https://example.com/cover.jpg",
                    publisher = "출판사",
                    itemPage = 320,
                    record = emptyMap(),
                    memo = emptyMap(),
                ),
            )

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .with(bookAuth())
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상세 조회 성공"))
            .andExpect(jsonPath("$.data.title").value("책 제목"))
            .andExpect(jsonPath("$.data.itemPage").value(320))
            .andExpect(jsonPath("$.data.record").isMap)
            .andExpect(jsonPath("$.data.memo").isMap)
    }

    @Test
    fun `duplication should return 403 when isbn already exists`() {
        given(bookQueryService.checkDuplication(1, "9788937462788"))
            .willThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "책 중복"))

        mockMvc.perform(
            get("/api/v1/book/")
                .with(bookAuth())
                .queryParam("isbn", "9788937462788"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("책 중복"))
    }

    @Test
    fun `create should return legacy created envelope`() {
        val request = CreateBookRequest(
            book_isbn = null,
            garden_no = 1,
            book_title = "책",
            book_info = "소개",
            book_author = "저자",
            book_publisher = "출판사",
            book_tree = null,
            book_image_url = null,
            book_status = 2,
            book_page = 300,
        )

        given(bookCommandService.createBook(1, request))
            .willReturn(CreateBookResponse(book_no = 77))

        mockMvc.perform(
            post("/api/v1/book/")
                .with(bookAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_no":1,"book_title":"책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_status":2,"book_page":300}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 등록 성공"))
            .andExpect(jsonPath("$.data.book_no").value(77))
    }

    @Test
    fun `delete book should return legacy success envelope`() {
        doReturn("책 삭제 성공")
            .`when`(bookCommandService)
            .deleteBook(1, 10)

        mockMvc.perform(
            delete("/api/v1/book/")
                .with(bookAuth())
                .queryParam("book_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 삭제 성공"))
    }

    @Test
    fun `create should return 400 when book status is missing`() {
        mockMvc.perform(
            post("/api/v1/book/")
                .with(bookAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_no":1,"book_title":"책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_page":300}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }

    @Test
    fun `create read should return legacy shape without internal ids`() {
        val request = CreateReadRequest(
            book_no = 10,
            book_start_date = null,
            book_end_date = null,
            book_current_page = 20,
        )

        given(bookReadService.createRead(1, request))
            .willReturn(
                CreateReadResponse(
                    book_current_page = 20,
                    percent = 20.0,
                ),
            )

        mockMvc.perform(
            post("/api/v1/book/read")
                .with(bookAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":10,"book_current_page":20}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))
            .andExpect(jsonPath("$.data.book_current_page").value(20))
            .andExpect(jsonPath("$.data.percent").value(20.0))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_no").doesNotExist())
    }

    @Test
    fun `upload image should return legacy created envelope`() {
        val file = MockMultipartFile(
            "file",
            "cover.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        doReturn("이미지 업로드 성공")
            .`when`(bookImageService)
            .uploadBookImage(10, file)

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(file)
                .with(bookAuth())
                .param("book_no", "10"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))
    }

    @Test
    fun `delete image should return legacy created envelope`() {
        doReturn("이미지 삭제 성공")
            .`when`(bookImageService)
            .deleteBookImage(10)

        mockMvc.perform(
            delete("/api/v1/book/image")
                .with(bookAuth())
                .queryParam("book_no", "10"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))
    }

    @Test
    fun `status should keep legacy paging fields`() {
        given(bookQueryService.getBookStatus(1, 2, null, 1, 10))
            .willReturn(
                BookStatusResponse(
                    current_page = 1,
                    max_page = 1,
                    total_items = 1,
                    page_size = 10,
                    list = listOf(
                        BookStatusItemResponse(
                            book_no = 10,
                            book_title = "책",
                            book_author = "저자",
                            book_publisher = "출판사",
                            book_info = "소개",
                            book_image_url = null,
                            book_tree = null,
                            book_status = 0,
                            percent = 25.0,
                            book_page = 300,
                            garden_no = 2,
                        ),
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/book/status")
                .with(bookAuth())
                .queryParam("garden_no", "2")
                .queryParam("page", "1")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상태 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total_items").value(1))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list[0].book_no").value(10))
            .andExpect(jsonPath("$.data.list[0].book_title").value("책"))
            .andExpect(jsonPath("$.data.list[0].book_status").value(0))
            .andExpect(jsonPath("$.data.list[0].percent").value(25.0))
    }

    @Test
    fun `read should keep legacy typo and zero defaults`() {
        given(bookQueryService.getBookRead(10))
            .willReturn(
                BookReadDetailResponse(
                    user_no = 1,
                    book_title = "책",
                    book_author = "저자",
                    book_publisher = "출판사",
                    book_info = "소개",
                    book_image_url = null,
                    book_tree = null,
                    book_status = 0,
                    book_page = 300,
                    garden_no = null,
                    book_current_page = 0,
                    percent = 0.0,
                    book_read_list = listOf(
                        BookReadHistoryItemResponse(
                            id = 1,
                            book_current_page = 20,
                            book_start_date = "2026-04-06T11:00:00",
                            book_end_date = null,
                            created_ad = "2026-04-06T11:00:00",
                        ),
                    ),
                    memo_list = listOf(
                        BookReadMemoItemResponse(
                            id = 1,
                            memo_content = "메모",
                            memo_like = true,
                            memo_created_at = "2026-04-06T10:30:00",
                        ),
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/book/read")
                .with(bookAuth())
                .queryParam("book_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 기록 조회 성공"))
            .andExpect(jsonPath("$.data.user_no").value(1))
            .andExpect(jsonPath("$.data.book_no").doesNotExist())
            .andExpect(jsonPath("$.data.book_current_page").value(0))
            .andExpect(jsonPath("$.data.percent").value(0.0))
            .andExpect(jsonPath("$.data.book_read_list[0].created_ad").value("2026-04-06T11:00:00"))
            .andExpect(jsonPath("$.data.book_read_list[0].book_start_date").value("2026-04-06T11:00:00"))
            .andExpect(jsonPath("$.data.book_read_list[0].created_at").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_list[0].book_read_no").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[0].memo_content").value("메모"))
            .andExpect(jsonPath("$.data.memo_list[0].memo_created_at").value("2026-04-06T10:30:00"))
            .andExpect(jsonPath("$.data.memo_list[0].book_no").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[0].book_title").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[0].image_url").doesNotExist())
    }

    private fun bookAuth() =
        authentication(
            UsernamePasswordAuthenticationToken(
                LegacyAuthenticationPrincipal(1, "테스터"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            ),
        )
}
