package std.nooook.readinggardenkotlin.modules.memo

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
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListItemResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListResponse
import std.nooook.readinggardenkotlin.modules.memo.service.MemoService

@SpringBootTest
@AutoConfigureMockMvc
class MemoControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var memoService: MemoService

    @Test
    fun `get memo should return legacy success envelope`() {
        given(memoService.getMemoList(userNo = 1, page = 2, pageSize = 10))
            .willReturn(
                MemoListResponse(
                    current_page = 2,
                    max_page = 3,
                    total = 21,
                    page_size = 10,
                    list = listOf(
                        MemoListItemResponse(
                            id = 9,
                            book_no = 17,
                            book_title = "책 제목",
                            book_author = "저자",
                            book_image_url = "https://example.com/book.jpg",
                            memo_content = "메모 내용",
                            memo_like = true,
                            image_url = "",
                            memo_created_at = "2026-04-06T12:00:00",
                        ),
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("page", "2")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(2))
            .andExpect(jsonPath("$.data.max_page").value(3))
            .andExpect(jsonPath("$.data.total").value(21))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list[0].id").value(9))
            .andExpect(jsonPath("$.data.list[0].book_no").value(17))
            .andExpect(jsonPath("$.data.list[0].image_url").value(""))
    }
}
