package std.nooook.readinggardenkotlin.common

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import std.nooook.readinggardenkotlin.common.exception.ApiException
import std.nooook.readinggardenkotlin.common.exception.ErrorCode

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(LegacyErrorEnvelopeMvcTest.ErrorController::class)
class LegacyErrorEnvelopeMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `api exception should return legacy envelope`() {
        mockMvc.perform(get("/api/test/error"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("legacy bad request"))
    }

    @Test
    fun `unhandled exception should not expose internal message`() {
        mockMvc.perform(get("/api/test/internal-error"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.resp_code").value(500))
            .andExpect(jsonPath("$.resp_msg").value("An unexpected error occurred."))
    }

    @RestController
    @RequestMapping("/api/test")
    class ErrorController {
        @GetMapping("/error")
        fun throwError(): Nothing = throw ApiException(ErrorCode.BAD_REQUEST, "legacy bad request")

        @GetMapping("/internal-error")
        fun throwInternalError(): Nothing = throw IllegalStateException("database password leaked")
    }
}
