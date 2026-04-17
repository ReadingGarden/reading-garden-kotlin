package std.nooook.readinggardenkotlin.modules.app

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.TestcontainersConfiguration

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AppVersionControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `GET app version for ios returns seeded data from flyway`() {
        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "ios")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("앱 버전 조회 성공"))
            .andExpect(jsonPath("$.data.platform").value("ios"))
            .andExpect(jsonPath("$.data.latest_version").value("1.1.0"))
            .andExpect(jsonPath("$.data.min_supported_version").value("1.0.0"))
            .andExpect(jsonPath("$.data.store_url").value("https://apps.apple.com/app/id<APP_ID>"))
    }

    @Test
    fun `GET app version for android returns seeded data from flyway`() {
        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "android")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.platform").value("android"))
            .andExpect(jsonPath("$.data.latest_version").value("1.1.0"))
            .andExpect(jsonPath("$.data.store_url").value("https://play.google.com/store/apps/details?id=<PKG>"))
    }

    @Test
    fun `GET app version with invalid platform returns 400`() {
        mockMvc.perform(
            get("/api/v1/app/version")
                .param("platform", "windows")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
    }
}
