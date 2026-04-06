package std.nooook.readinggardenkotlin.modules.book

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.controller.BookDetailResponse
import std.nooook.readinggardenkotlin.modules.book.service.BookService

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var bookService: BookService

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
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
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
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
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
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
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
}
