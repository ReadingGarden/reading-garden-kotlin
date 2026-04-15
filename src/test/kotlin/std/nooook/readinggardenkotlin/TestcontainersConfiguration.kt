package std.nooook.readinggardenkotlin

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> = postgres

    companion object {
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
                .withCommand("postgres", "-c", "max_connections=300")
    }
}
