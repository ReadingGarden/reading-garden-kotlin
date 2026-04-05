package std.nooook.readinggardenkotlin.contracts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest
class LegacyContractFixtureTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `legacy login success fixture should expose resp envelope`() {
        val fixture = readFixture("contracts/legacy/auth/login-success.json")

        assertThat(fixture["resp_code"].asInt()).isEqualTo(200)
        assertThat(fixture["resp_msg"].asText()).isEqualTo("로그인 성공")
        assertThat(fixture["data"].has("access_token")).isTrue()
        assertThat(fixture["data"].has("refresh_token")).isTrue()
    }

    @Test
    fun `legacy login failure fixture should expose error envelope`() {
        val fixture = readFixture("contracts/legacy/auth/login-invalid-password.json")

        assertThat(fixture["resp_code"].asInt()).isEqualTo(400)
        assertThat(fixture["resp_msg"].asText()).isEqualTo("비밀번호가 일치하지 않습니다.")
    }

    private fun readFixture(path: String): JsonNode =
        ClassPathResource(path).inputStream.use(objectMapper::readTree)
}
