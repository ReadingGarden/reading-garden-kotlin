package std.nooook.readinggardenkotlin.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import std.nooook.readinggardenkotlin.common.api.LegacyResponses

@Component
class LegacySecurityResponseWriter {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    fun writeUnauthorized(response: HttpServletResponse) {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
    }

    fun writeForbidden(response: HttpServletResponse) {
        write(response, HttpServletResponse.SC_FORBIDDEN, "Access denied")
    }

    private fun write(
        response: HttpServletResponse,
        status: Int,
        message: String,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, LegacyResponses.error(status = status, message = message))
    }
}
