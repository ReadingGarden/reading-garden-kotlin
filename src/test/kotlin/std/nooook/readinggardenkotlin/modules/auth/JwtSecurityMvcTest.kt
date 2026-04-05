package std.nooook.readinggardenkotlin.modules.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtSecurityMvcTest.ProtectedAuthTestController::class)
class JwtSecurityMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `protected auth profile should reject missing bearer token`() {
        mockMvc.perform(get("/api/v1/auth"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @RestController
    @RequestMapping("/api/v1/auth")
    class ProtectedAuthTestController {
        @GetMapping
        fun profile(): Map<String, String> = mapOf("user_nick" to "tester")
    }
}
