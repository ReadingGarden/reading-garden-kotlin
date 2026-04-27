package std.nooook.readinggardenkotlin.common.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class HttpRequestLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!shouldLog(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        val startedAt = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            requestLogger.info(
                "http_request method={} path={} status={} duration_ms={} client_ip={}",
                request.method,
                sanitize(request.requestURI),
                response.status,
                durationMs,
                clientIp(request),
            )
        }
    }

    private fun shouldLog(path: String): Boolean {
        return path.startsWith("/api/") && !path.startsWith("/api/health")
    }

    private fun clientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return sanitize(forwardedFor ?: request.remoteAddr ?: "unknown")
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("\\s+"), "_")
    }

    companion object {
        private val requestLogger = LoggerFactory.getLogger(HttpRequestLoggingFilter::class.java)
    }
}
