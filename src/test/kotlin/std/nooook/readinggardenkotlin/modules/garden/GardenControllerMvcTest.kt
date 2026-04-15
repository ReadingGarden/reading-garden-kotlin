package std.nooook.readinggardenkotlin.modules.garden

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.UpdateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenDetailBookResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenDetailResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenListItemResponse
import std.nooook.readinggardenkotlin.modules.garden.controller.GardenListMemberResponse
import std.nooook.readinggardenkotlin.modules.garden.service.GardenCommandService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenMembershipService
import std.nooook.readinggardenkotlin.modules.garden.service.GardenQueryService

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class GardenControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var gardenCommandService: GardenCommandService

    @MockitoBean
    private lateinit var gardenMembershipService: GardenMembershipService

    @MockitoBean
    private lateinit var gardenQueryService: GardenQueryService

    @Test
    fun `create garden should return legacy success envelope`() {
        given(
            gardenCommandService.createGarden(
                userId = 1L,
                request = CreateGardenRequest(
                    garden_title = "새 가든",
                    garden_info = "소개",
                    garden_color = "blue",
                ),
            ),
        ).willReturn(
            CreateGardenResponse(
                garden_no = 10,
                garden_title = "새 가든",
                garden_info = "소개",
                garden_color = "blue",
            ),
        )

        mockMvc.perform(
            post("/api/v1/garden/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"새 가든","garden_info":"소개","garden_color":"blue"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("가든 추가 성공"))
            .andExpect(jsonPath("$.data.garden_no").value(10))
            .andExpect(jsonPath("$.data.garden_title").value("새 가든"))
        verify(gardenCommandService).createGarden(
            1,
            CreateGardenRequest(
                garden_title = "새 가든",
                garden_info = "소개",
                garden_color = "blue",
            ),
        )
    }

    @Test
    fun `get garden list should return legacy success envelope`() {
        given(gardenQueryService.getGardenList(1))
            .willReturn(
                listOf(
                    GardenListItemResponse(
                        garden_no = 10,
                        garden_title = "리스트 가든",
                        garden_info = "리스트 소개",
                        garden_color = "blue",
                        garden_members = 3,
                        book_count = 2,
                        garden_created_at = "2026-04-06T09:00:00",
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/garden/list")
                .with(gardenAuth()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 리스트 조회 성공"))
            .andExpect(jsonPath("$.data[0].garden_no").value(10))
            .andExpect(jsonPath("$.data[0].garden_title").value("리스트 가든"))
            .andExpect(jsonPath("$.data[0].garden_info").value("리스트 소개"))
            .andExpect(jsonPath("$.data[0].garden_members").value(3))
            .andExpect(jsonPath("$.data[0].book_count").value(2))
            .andExpect(jsonPath("$.data[0].garden_created_at").value("2026-04-06T09:00:00"))
            .andExpect(jsonPath("$.data").isArray)
        verify(gardenQueryService).getGardenList(1)
    }

    @Test
    fun `get garden detail should return legacy success envelope`() {
        given(gardenQueryService.getGardenDetail(1, 10))
            .willReturn(
                GardenDetailResponse(
                    garden_no = 10,
                    garden_title = "상세 가든",
                    garden_info = "상세 소개",
                    garden_color = "green",
                    garden_created_at = "2026-04-06T08:00:00",
                    book_list = listOf(
                        GardenDetailBookResponse(
                            book_no = 31,
                            book_isbn = "9788937462788",
                            book_title = "가든 책",
                            book_author = "저자",
                            book_publisher = "출판사",
                            book_info = "설명",
                            book_image_url = "https://example.com/book.jpg",
                            book_tree = "seed",
                            book_status = 2,
                            percent = 50.0,
                            user_no = 3,
                            book_page = 320,
                        ),
                    ),
                    garden_members = listOf(
                        GardenListMemberResponse(
                            user_no = 3,
                            user_nick = "멤버",
                            user_image = "https://example.com/member.jpg",
                            garden_leader = false,
                            garden_sign_date = "2026-04-06T10:00:00",
                        ),
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/garden/detail")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 상세 조회 성공"))
            .andExpect(jsonPath("$.data.garden_no").value(10))
            .andExpect(jsonPath("$.data.garden_title").value("상세 가든"))
            .andExpect(jsonPath("$.data.garden_info").value("상세 소개"))
            .andExpect(jsonPath("$.data.garden_color").value("green"))
            .andExpect(jsonPath("$.data.garden_created_at").value("2026-04-06T08:00:00"))
            .andExpect(jsonPath("$.data.book_list").isArray)
            .andExpect(jsonPath("$.data.book_list[0].book_no").value(31))
            .andExpect(jsonPath("$.data.book_list[0].book_isbn").value("9788937462788"))
            .andExpect(jsonPath("$.data.book_list[0].book_author").value("저자"))
            .andExpect(jsonPath("$.data.book_list[0].book_publisher").value("출판사"))
            .andExpect(jsonPath("$.data.book_list[0].book_info").value("설명"))
            .andExpect(jsonPath("$.data.book_list[0].book_image_url").value("https://example.com/book.jpg"))
            .andExpect(jsonPath("$.data.book_list[0].book_tree").value("seed"))
            .andExpect(jsonPath("$.data.book_list[0].book_status").value(2))
            .andExpect(jsonPath("$.data.book_list[0].percent").value(50.0))
            .andExpect(jsonPath("$.data.book_list[0].user_no").value(3))
            .andExpect(jsonPath("$.data.book_list[0].book_page").value(320))
            .andExpect(jsonPath("$.data.garden_members").isArray)
            .andExpect(jsonPath("$.data.garden_members[0].user_no").value(3))
            .andExpect(jsonPath("$.data.garden_members[0].user_nick").value("멤버"))
            .andExpect(jsonPath("$.data.garden_members[0].user_image").value("https://example.com/member.jpg"))
            .andExpect(jsonPath("$.data.garden_members[0].garden_leader").value(false))
            .andExpect(jsonPath("$.data.garden_members[0].garden_sign_date").value("2026-04-06T10:00:00"))
        verify(gardenQueryService).getGardenDetail(1, 10)
    }

    @Test
    fun `get garden detail should require garden_no query parameter`() {
        mockMvc.perform(
            get("/api/v1/garden/detail")
                .with(gardenAuth()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(HttpStatus.BAD_REQUEST.value()))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'garden_no' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("garden_no"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `update garden should return legacy success envelope`() {
        given(
            gardenCommandService.updateGarden(
                userId = 1L,
                gardenNo = 10,
                request = UpdateGardenRequest(
                    garden_title = "수정된 가든",
                    garden_info = "수정 소개",
                    garden_color = "green",
                ),
            ),
        ).willReturn("가든 수정 성공")

        mockMvc.perform(
            put("/api/v1/garden/")
                .with(gardenAuth())
                .queryParam("garden_no", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"수정된 가든","garden_info":"수정 소개","garden_color":"green"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 수정 성공"))
            .andExpect(jsonPath("$.data").isMap)
            .andExpect(jsonPath("$.data").isEmpty)
        verify(gardenCommandService).updateGarden(
            1,
            10,
            UpdateGardenRequest(
                garden_title = "수정된 가든",
                garden_info = "수정 소개",
                garden_color = "green",
            ),
        )
    }

    @Test
    fun `delete garden should return legacy success envelope`() {
        given(gardenCommandService.deleteGarden(1, 10))
            .willReturn("가든 삭제 성공")

        mockMvc.perform(
            delete("/api/v1/garden/")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 삭제 성공"))
        verify(gardenCommandService).deleteGarden(1, 10)
    }

    @Test
    fun `move garden should require to_garden_no query parameter`() {
        mockMvc.perform(
            put("/api/v1/garden/to")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(HttpStatus.BAD_REQUEST.value()))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'to_garden_no' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("to_garden_no"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `move garden book should return legacy success envelope`() {
        given(gardenCommandService.moveGardenBook(1, 10, 20))
            .willReturn("가든 책 이동 성공")

        mockMvc.perform(
            put("/api/v1/garden/to")
                .with(gardenAuth())
                .queryParam("garden_no", "10")
                .queryParam("to_garden_no", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 책 이동 성공"))
        verify(gardenCommandService).moveGardenBook(1, 10, 20)
    }

    @Test
    fun `delete garden member should require garden_no query parameter`() {
        mockMvc.perform(
            delete("/api/v1/garden/member")
                .with(gardenAuth())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(HttpStatus.BAD_REQUEST.value()))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'garden_no' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("garden_no"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `delete garden member should return legacy success envelope`() {
        given(gardenMembershipService.leaveGardenMember(1, 10))
            .willReturn("가든 탈퇴 성공")

        mockMvc.perform(
            delete("/api/v1/garden/member")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 탈퇴 성공"))
        verify(gardenMembershipService).leaveGardenMember(1, 10)
    }

    @Test
    fun `update garden member should return legacy success envelope`() {
        given(gardenMembershipService.updateGardenMember(1, 10, 3))
            .willReturn("가든 멤버 변경 성공")

        mockMvc.perform(
            put("/api/v1/garden/member")
                .with(gardenAuth())
                .queryParam("garden_no", "10")
                .queryParam("user_no", "3"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 멤버 변경 성공"))
        verify(gardenMembershipService).updateGardenMember(1, 10, 3)
    }

    @Test
    fun `update garden main should return legacy success envelope`() {
        given(gardenCommandService.updateGardenMain(1, 10))
            .willReturn("가든 메인 변경 성공")

        mockMvc.perform(
            put("/api/v1/garden/main")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("가든 메인 변경 성공"))
        verify(gardenCommandService).updateGardenMain(1, 10)
    }

    @Test
    fun `invite garden should return legacy created envelope`() {
        given(gardenMembershipService.inviteGardenMember(1, 10))
            .willReturn("가든 초대 완료")

        mockMvc.perform(
            post("/api/v1/garden/invite")
                .with(gardenAuth())
                .queryParam("garden_no", "10"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("가든 초대 완료"))
            .andExpect(jsonPath("$.data").doesNotExist())
        verify(gardenMembershipService).inviteGardenMember(1, 10)
    }

    private fun gardenAuth() =
        authentication(
            UsernamePasswordAuthenticationToken(
                LegacyAuthenticationPrincipal(1L, "테스터"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            ),
        )
}
