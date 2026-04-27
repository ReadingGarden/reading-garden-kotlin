package std.nooook.readinggardenkotlin.common.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HttpRequestLoggingFilterTest {
    private val logger = LoggerFactory.getLogger(HttpRequestLoggingFilter::class.java) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()
    private val filter = HttpRequestLoggingFilter()

    @BeforeEach
    fun setUp() {
        listAppender.start()
        logger.addAppender(listAppender)
        logger.level = Level.INFO
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `api request should be logged after response completes`() {
        val request = MockHttpServletRequest("POST", "/api/v1/book/register").apply {
            addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
        }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, servletResponse ->
            (servletResponse as HttpServletResponse).status = 201
        }

        filter.doFilter(request, response, chain)

        val messages = listAppender.list.map { it.formattedMessage }
        assertTrue(
            messages.any {
                it.contains("http_request") &&
                    it.contains("method=POST") &&
                    it.contains("path=/api/v1/book/register") &&
                    it.contains("status=201") &&
                    it.contains("client_ip=203.0.113.9") &&
                    it.contains("duration_ms=")
            },
        )
    }

    @Test
    fun `health and actuator requests should not be logged`() {
        filter.doFilter(
            MockHttpServletRequest("GET", "/api/health"),
            MockHttpServletResponse(),
            FilterChain { _, _ -> },
        )
        filter.doFilter(
            MockHttpServletRequest("GET", "/actuator/prometheus"),
            MockHttpServletResponse(),
            FilterChain { _, _ -> },
        )

        assertTrue(listAppender.list.isEmpty())
    }
}
