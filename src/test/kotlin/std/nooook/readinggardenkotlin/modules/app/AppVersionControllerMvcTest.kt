package std.nooook.readinggardenkotlin.modules.app

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import std.nooook.readinggardenkotlin.modules.app.controller.AppVersionResponse
import std.nooook.readinggardenkotlin.modules.app.service.AppVersionQueryService

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AppVersionControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var appVersionQueryService: AppVersionQueryService

    @Test
    fun `get app version for ios returns legacy envelope with snake_case data`() {
        given(appVersionQueryService.getByPlatform("ios"))
            .willReturn(
                AppVersionResponse(
                    platform = "ios",
                    latest_version = "1.2.0",
                    min_supported_version = "1.0.0",
                    store_url = "https://apps.apple.com/app/id1234567890",
                ),
            )

        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "ios")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("앱 버전 조회 성공"))
            .andExpect(jsonPath("$.data.platform").value("ios"))
            .andExpect(jsonPath("$.data.latest_version").value("1.2.0"))
            .andExpect(jsonPath("$.data.min_supported_version").value("1.0.0"))
            .andExpect(jsonPath("$.data.store_url").value("https://apps.apple.com/app/id1234567890"))
    }

    @Test
    fun `get app version for android returns legacy envelope`() {
        given(appVersionQueryService.getByPlatform("android"))
            .willReturn(
                AppVersionResponse(
                    platform = "android",
                    latest_version = "1.2.0",
                    min_supported_version = "1.0.0",
                    store_url = "https://play.google.com/store/apps/details?id=com.example",
                ),
            )

        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "android")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("앱 버전 조회 성공"))
            .andExpect(jsonPath("$.data.platform").value("android"))
            .andExpect(jsonPath("$.data.latest_version").value("1.2.0"))
            .andExpect(jsonPath("$.data.min_supported_version").value("1.0.0"))
            .andExpect(jsonPath("$.data.store_url").value("https://play.google.com/store/apps/details?id=com.example"))
    }

    @Test
    fun `missing platform parameter returns 400`() {
        mockMvc.perform(
            get("/api/v1/app/version")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }

    @Test
    fun `invalid platform value returns 400`() {
        given(appVersionQueryService.getByPlatform("windows"))
            .willThrow(
                ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "platform must be one of: ios, android",
                ),
            )

        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "windows")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }

    @Test
    fun `platform not found in DB returns 404`() {
        given(appVersionQueryService.getByPlatform("ios"))
            .willThrow(
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "App version not found for platform: ios",
                ),
            )

        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "ios")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resp_code").value(404))
    }

    @Test
    fun `endpoint is accessible without authentication`() {
        given(appVersionQueryService.getByPlatform("ios"))
            .willReturn(
                AppVersionResponse(
                    platform = "ios",
                    latest_version = "1.2.0",
                    min_supported_version = "1.0.0",
                    store_url = "https://apps.apple.com/app/id1234567890",
                ),
            )

        // No .with(authentication(...)) — must succeed anonymously
        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "ios")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
    }
}
