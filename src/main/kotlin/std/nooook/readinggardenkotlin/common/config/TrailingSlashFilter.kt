package std.nooook.readinggardenkotlin.common.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrailingSlashFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val uri = request.requestURI
        if (uri.length > 1 && uri.endsWith("/")) {
            filterChain.doFilter(TrailingSlashRequestWrapper(request), response)
        } else {
            filterChain.doFilter(request, response)
        }
    }

    private class TrailingSlashRequestWrapper(
        request: HttpServletRequest,
    ) : HttpServletRequestWrapper(request) {
        private val trimmedUri = request.requestURI.trimEnd('/')
        private val trimmedServletPath = request.servletPath.trimEnd('/')

        override fun getRequestURI(): String = trimmedUri
        override fun getServletPath(): String = trimmedServletPath
    }
}
