package std.nooook.readinggardenkotlin.modules.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `protected auth profile should reject missing bearer token`() {
        mockMvc.perform(get("/api/v1/auth/"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
    }
}
