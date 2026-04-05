package std.nooook.readinggardenkotlin.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class LegacyAccessDeniedHandler(
    private val legacySecurityResponseWriter: LegacySecurityResponseWriter,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        if (response.isCommitted) {
            return
        }

        legacySecurityResponseWriter.writeForbidden(response)
    }
}
