package std.nooook.readinggardenkotlin.common.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Authorization 헤더에 `Bearer {access_token}` 형식으로 전달합니다.",
)
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Reading Garden Legacy API")
                .version("v1")
                .description(
                    "Legacy Python backend cutover target documented for Flutter compatibility. " +
                        "모든 요청과 응답은 레거시 snake_case 필드명과 envelope 규약을 기준으로 문서화합니다.",
                ),
        )
        .components(Components())
        .servers(
            listOf(
                Server()
                    .url("http://localhost:8080")
                    .description("Local development server"),
            ),
        )
        .tags(
            listOf(
                Tag().name("Auth").description("회원가입, 로그인, 토큰 재발급, 프로필, 비밀번호 재설정"),
                Tag().name("Book").description("책 검색, 등록, 상태 조회, 독서 기록, 책 이미지"),
                Tag().name("Garden").description("가든 조회, 생성, 수정, 이동, 멤버 관리"),
                Tag().name("Memo").description("메모 조회, 생성, 수정, 좋아요, 메모 이미지"),
                Tag().name("Push").description("푸시 설정 조회 및 수정, 운영성 푸시 전송"),
            ),
        )
}
