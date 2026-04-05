package std.nooook.readinggardenkotlin.common

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@Import(InfrastructureMvcTest.ValidationTestController::class)
class InfrastructureMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `swagger api docs should be exposed`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Reading Garden API"))
    }

    @Test
    fun `request body validation errors should return problem detail`() {
        mockMvc.perform(
            post("/api/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
    }

    @Test
    fun `request parameter validation errors should return problem detail`() {
        mockMvc.perform(get("/api/test/validation").param("count", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].parameter").value("count"))
    }

    @Validated
    @RestController
    @RequestMapping("/api/test")
    class ValidationTestController {

        @PostMapping("/validation")
        fun validateBody(
            @Valid @RequestBody request: ValidationRequest,
        ): Map<String, String> = mapOf("name" to request.name)

        @GetMapping("/validation")
        fun validateParam(
            @RequestParam @Min(1) count: Int,
        ): Map<String, Int> = mapOf("count" to count)
    }

    data class ValidationRequest(
        @field:NotBlank(message = "name must not be blank")
        val name: String,
    )
}
