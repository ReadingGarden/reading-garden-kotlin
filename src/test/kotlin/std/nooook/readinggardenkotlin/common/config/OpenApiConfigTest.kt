package std.nooook.readinggardenkotlin.common.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private lateinit var apiDocs: JsonNode

    @BeforeEach
    fun setUp() {
        val response = mockMvc.perform(
            get("/v3/api-docs").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andReturn()

        apiDocs = objectMapper.readTree(response.response.contentAsString)
    }

    @Test
    fun `open api should expose legacy metadata and bearer auth`() {
        assertThat(apiDocs.at("/info/title").asText()).isEqualTo("Reading Garden Legacy API")
        assertThat(apiDocs.at("/info/description").asText()).contains("Legacy").contains("Flutter")
        assertThat(apiDocs.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http")
        assertThat(apiDocs.at("/tags").toString()).contains("Auth", "Book", "Garden", "Memo", "Push")
    }

    @Test
    fun `auth endpoints should describe signup and protected profile contract`() {
        val signup = requireOperation("post", "/api/v1/auth", "/api/v1/auth/")
        val profile = requireOperation("get", "/api/v1/auth", "/api/v1/auth/")

        assertThat(signup.at("/summary").asText()).contains("회원가입")
        assertThat(signup.at("/description").asText()).contains("user_email").contains("user_password")
        assertThat(signup.at("/requestBody/content/application~1json/schema/\$ref").asText()).contains("CreateUserRequest")
        assertThat(signup.at("/responses/201").isMissingNode).isFalse
        assertThat(signup.at("/responses/400").isMissingNode).isFalse

        assertThat(profile.at("/summary").asText()).contains("프로필")
        assertThat(profile.at("/security").isEmpty).isFalse
        assertThat(profile.at("/responses/200").isMissingNode).isFalse
        assertThat(profile.at("/responses/401").isMissingNode).isFalse
    }

    @Test
    fun `app and auth endpoints should expose concrete response examples`() {
        val appVersion = requireOperation("get", "/api/v1/app/version")
        val login = requireOperation("post", "/api/v1/auth/login")
        val logout = requireOperation("post", "/api/v1/auth/logout")
        val refresh = requireOperation("post", "/api/v1/auth/refresh")
        val findPassword = requireOperation("post", "/api/v1/auth/find-password")

        assertResponseExamplesContain(appVersion, "400", "platform must be one of: ios, android")
        assertResponseExamplesContain(appVersion, "404", "App version not found for platform: ios")

        assertResponseExamplesContain(login, "200", "로그인 성공")
        assertResponseExamplesContain(logout, "200", "로그아웃 성공")
        assertResponseExamplesContain(refresh, "200", "토큰 발급 성공")
        assertResponseExamplesContain(findPassword, "200", "메일이 발송되었습니다. 확인해주세요.")

        assertSchemaExample("AppVersionResponse", "latest_version", "1.2.0")
        assertSchemaExample("UserProfileResponse", "user_nick", "임의닉네임")
        assertSchemaExample("UserSummaryResponse", "user_fcm", "fcm-token-value")
    }

    @Test
    fun `book endpoints should expose query and multipart documentation`() {
        val search = requireOperation("get", "/api/v1/book/search")
        val upload = requireOperation("post", "/api/v1/book/image")

        assertThat(search.at("/summary").asText()).contains("책 검색")
        assertThat(search.at("/parameters").toString()).contains("query", "start", "maxResults")

        assertThat(upload.at("/summary").asText()).contains("이미지")
        assertThat(upload.at("/requestBody/content/multipart~1form-data").isMissingNode).isFalse
        assertThat(upload.at("/security").isEmpty).isFalse
        assertThat(upload.at("/responses/201").isMissingNode).isFalse
        assertThat(upload.at("/responses/400").isMissingNode).isFalse
        assertThat(upload.at("/responses/401").isMissingNode).isFalse
    }

    @Test
    fun `garden memo and push endpoints should expose legacy descriptions`() {
        val gardenList = requireOperation("get", "/api/v1/garden/list")
        val memoImage = requireOperation("post", "/api/v1/memo/image")
        val pushUpdate = requireOperation("put", "/api/v1/push", "/api/v1/push/")

        assertThat(gardenList.at("/tags").toString()).contains("Garden")
        assertThat(gardenList.at("/description").asText()).contains("가든")

        assertThat(memoImage.at("/requestBody/content/multipart~1form-data").isMissingNode).isFalse
        assertThat(memoImage.at("/security").isEmpty).isFalse

        assertThat(pushUpdate.at("/summary").asText()).contains("푸시")
        assertThat(pushUpdate.at("/requestBody/content/application~1json").isMissingNode).isFalse
    }

    @Test
    fun `legacy response schema should document envelope fields`() {
        val httpResponse = requireSchema("LegacyHttpResponse")
        val dataResponse = requireSchema("LegacyDataResponse")

        assertThat(httpResponse.at("/description").asText()).contains("레거시")
        assertThat(httpResponse.at("/properties").toString()).contains("resp_code", "resp_msg", "errors")
        assertThat(dataResponse.at("/description").asText()).contains("레거시")
        assertThat(dataResponse.at("/properties").toString()).contains("resp_code", "resp_msg", "data")
    }

    @Test
    fun `open api should use same origin server instead of hard coded localhost`() {
        assertThat(apiDocs.at("/servers/0/url").asText()).isEqualTo("/")
        assertThat(apiDocs.at("/servers/0/url").asText()).doesNotContain("localhost:8080")
    }

    private fun requireSchema(name: String): JsonNode {
        val schema = apiDocs.at("/components/schemas/$name")
        require(!schema.isMissingNode) { "Schema not found: $name" }
        return schema
    }

    private fun assertResponseExamplesContain(operation: JsonNode, responseCode: String, expectedText: String) {
        val content = operation.at("/responses/$responseCode/content")
        assertThat(content.isMissingNode).isFalse

        val exampleValues = mutableListOf<JsonNode>()
        val mediaTypeNames = content.fieldNames()
        while (mediaTypeNames.hasNext()) {
            val mediaTypeNode = content.get(mediaTypeNames.next()) ?: continue
            mediaTypeNode.get("example")?.takeUnless { it.isMissingNode }?.let(exampleValues::add)

            val examplesNode = mediaTypeNode.get("examples")
            val exampleNames = examplesNode?.fieldNames()
            while (exampleNames != null && exampleNames.hasNext()) {
                val exampleNode = examplesNode.get(exampleNames.next())?.get("value")?.takeUnless { it.isMissingNode }
                if (exampleNode != null) {
                    exampleValues.add(exampleNode)
                }
            }
        }

        assertThat(exampleValues).isNotEmpty
        assertThat(exampleValues).anySatisfy { example ->
            assertThat(example.renderExampleText()).contains(expectedText)
        }
    }

    private fun assertSchemaExample(schemaName: String, propertyName: String, expectedExample: String) {
        val example = requireSchema(schemaName).at("/properties/$propertyName/example")
        assertThat(example.isMissingNode).isFalse
        assertThat(example.asText()).isEqualTo(expectedExample)
    }

    private fun JsonNode.renderExampleText(): String = when {
        isTextual || isNumber || isBoolean || isNull -> asText()
        else -> toString()
    }

    private fun requireOperation(method: String, vararg candidates: String): JsonNode {
        val operation = candidates.firstNotNullOfOrNull { path ->
            val escapedPath = path.replace("~", "~0").replace("/", "~1")
            apiDocs.at("/paths/$escapedPath/$method").takeUnless { it.isMissingNode }
        }
        requireNotNull(operation) { "Operation not found for paths=${candidates.toList()} method=$method" }
        return operation
    }
}
