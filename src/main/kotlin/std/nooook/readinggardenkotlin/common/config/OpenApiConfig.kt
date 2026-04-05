package std.nooook.readinggardenkotlin.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Reading Garden API")
                .version("v1")
                .description("Legacy Python backend migration target built with Spring Boot and Kotlin."),
        )
}
