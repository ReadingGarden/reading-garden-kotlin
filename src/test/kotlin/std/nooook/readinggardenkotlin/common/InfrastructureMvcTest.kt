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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
            .andExpect(jsonPath("$.info.title").value("Reading Garden Legacy API"))
    }

    @Test
    @WithMockUser
    fun `request body validation errors should return legacy envelope`() {
        mockMvc.perform(
            post("/api/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":""}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request body validation failed."))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
            .andExpect(jsonPath("$.errors[0].message").value("name must not be blank"))
    }

    @Test
    @WithMockUser
    fun `request parameter validation errors should return legacy envelope`() {
        mockMvc.perform(get("/api/test/validation").param("count", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request parameter validation failed."))
            .andExpect(jsonPath("$.errors[0].parameter").value("count"))
    }

    @Test
    @WithMockUser
    fun `malformed json should return legacy envelope`() {
        mockMvc.perform(
            post("/api/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Failed to read request"))
    }

    @Test
    @WithMockUser
    fun `missing request parameter should return legacy envelope`() {
        mockMvc.perform(get("/api/test/required-param"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'name' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("name"))
    }

    @Test
    @WithMockUser
    fun `type mismatch should return legacy envelope`() {
        mockMvc.perform(get("/api/test/type-mismatch").param("count", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request parameter type mismatch."))
            .andExpect(jsonPath("$.errors[0].parameter").value("count"))
            .andExpect(jsonPath("$.errors[0].rejectedValue").value("abc"))
    }

    @Test
    @WithMockUser
    fun `missing request header should return legacy envelope`() {
        mockMvc.perform(get("/api/test/required-header"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required header 'X-Trace-Id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("X-Trace-Id"))
            .andExpect(jsonPath("$.errors[0].source").value("header"))
    }

    @Test
    @WithMockUser
    fun `validated model attribute errors should return structured legacy envelope`() {
        mockMvc.perform(get("/api/test/model-validation").param("name", "").param("page", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request parameter validation failed."))
            .andExpect(jsonPath("$.errors[?(@.parameter=='request' && @.field=='name')]").isNotEmpty)
            .andExpect(jsonPath("$.errors[?(@.parameter=='request' && @.field=='page')]").isNotEmpty)
    }

    @Test
    @WithMockUser
    fun `bind exception should return legacy envelope`() {
        mockMvc.perform(get("/api/test/bind-exception"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request binding failed."))
            .andExpect(jsonPath("$.errors[0].field").value("age"))
            .andExpect(jsonPath("$.errors[0].rejectedValue").value("abc"))
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

        @GetMapping("/required-param")
        fun requiredParam(
            @RequestParam name: String,
        ): Map<String, String> = mapOf("name" to name)

        @GetMapping("/type-mismatch")
        fun typeMismatch(
            @RequestParam count: Int,
        ): Map<String, Int> = mapOf("count" to count)

        @GetMapping("/required-header")
        fun requiredHeader(
            @RequestHeader("X-Trace-Id") traceId: String,
        ): Map<String, String> = mapOf("traceId" to traceId)

        @GetMapping("/model-validation")
        fun modelValidation(
            @Valid @ModelAttribute request: QueryValidationRequest,
        ): Map<String, Any> = mapOf("name" to request.name, "page" to request.page)

        @GetMapping("/bind-exception")
        fun bindException(): Nothing {
            val target = BindingRequest()
            val bindingResult = BeanPropertyBindingResult(target, "bindingRequest").apply {
                addError(FieldError("bindingRequest", "age", "abc", false, null, null, "Invalid value"))
            }
            throw BindException(bindingResult)
        }
    }

    data class ValidationRequest(
        @field:NotBlank(message = "name must not be blank")
        val name: String,
    )

    data class BindingRequest(
        val age: Int = 0,
    )

    data class QueryValidationRequest(
        @field:NotBlank(message = "name must not be blank")
        val name: String = "",
        @field:Min(value = 1, message = "page must be greater than or equal to 1")
        val page: Int = 0,
    )
}
