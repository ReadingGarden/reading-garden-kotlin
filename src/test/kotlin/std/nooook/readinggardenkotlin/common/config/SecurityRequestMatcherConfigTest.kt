package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.mock.web.MockHttpServletRequest

class SecurityRequestMatcherConfigTest {

    private val matcher = SecurityRequestMatcherConfig().publicEndpointRequestMatcher()

    @Test
    fun `actuator health and prometheus are public for internal monitoring scrape`() {
        assertTrue(matcher.matches(MockHttpServletRequest("GET", "/actuator/health")))
        assertTrue(matcher.matches(MockHttpServletRequest("GET", "/actuator/health/liveness")))
        assertTrue(matcher.matches(MockHttpServletRequest("GET", "/actuator/health/readiness")))
        assertTrue(matcher.matches(MockHttpServletRequest("GET", "/actuator/prometheus")))
    }

    @Test
    fun `sensitive actuator endpoints are not public`() {
        assertFalse(matcher.matches(MockHttpServletRequest("GET", "/actuator/env")))
        assertFalse(matcher.matches(MockHttpServletRequest("GET", "/actuator/configprops")))
        assertFalse(matcher.matches(MockHttpServletRequest("GET", "/actuator/beans")))
        assertFalse(matcher.matches(MockHttpServletRequest("GET", "/actuator/loggers")))
    }
}
