package std.nooook.readinggardenkotlin.modules.garden

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenRequest
import std.nooook.readinggardenkotlin.modules.garden.controller.CreateGardenResponse
import std.nooook.readinggardenkotlin.modules.garden.service.GardenService

@SpringBootTest
@AutoConfigureMockMvc
class GardenControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var gardenService: GardenService

    @Test
    fun `create garden should return legacy success envelope`() {
        given(
            gardenService.createGarden(
                userNo = 1,
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
                            LegacyAuthenticationPrincipal(1, "테스터"),
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
    }
}
