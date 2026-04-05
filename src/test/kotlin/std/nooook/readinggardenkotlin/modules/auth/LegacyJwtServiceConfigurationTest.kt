package std.nooook.readinggardenkotlin.modules.auth

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils
import std.nooook.readinggardenkotlin.modules.auth.service.LegacyJwtService

class LegacyJwtServiceConfigurationTest {
    @Test
    fun `missing hs256 key should fail fast at startup`() {
        assertThatThrownBy {
            startContext()
        }.cause().hasMessageContaining("app.security.hs256-key must not be blank")
    }

    @Test
    fun `invalid hs256 key should fail fast at startup`() {
        assertThatThrownBy {
            startContext("app.security.hs256-key=%%%")
        }.cause().hasMessageContaining("app.security.hs256-key must be valid Base64")
    }

    private fun startContext(vararg properties: String) =
        AnnotationConfigWebApplicationContext().apply {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this, *properties)
            register(LegacyJwtServiceTestConfiguration::class.java)
            refresh()
        }

    @Configuration(proxyBeanMethods = false)
    @Import(LegacyJwtService::class)
    class LegacyJwtServiceTestConfiguration {
        @Bean
        fun propertySourcesPlaceholderConfigurer() = PropertySourcesPlaceholderConfigurer()
    }
}
