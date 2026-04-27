package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.auth.oauth2.GoogleCredentials
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import org.slf4j.LoggerFactory

class HttpFcmClient(
    private val credentials: GoogleCredentials,
    private val projectId: String,
) : FcmClient {
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val fcmUrl = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

    override fun sendToMany(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<Map<String, Any>> {
        if (tokens.isEmpty()) {
            return emptyList()
        }

        refreshAccessToken()

        return tokens.map { token ->
            try {
                sendSingle(token, title, body, data)
            } catch (exception: Exception) {
                logger.warn(
                    "FCM send failed: tokenHash={}, errorCode={}, message={}",
                    token.sha256Prefix(),
                    exception::class.simpleName ?: "EXCEPTION",
                    exception.message,
                )
                mapOf(
                    "token" to token,
                    "result" to "failed",
                    "error_code" to (exception::class.simpleName ?: "EXCEPTION"),
                    "error" to (exception.message ?: "FCM send failed"),
                )
            }
        }
    }

    private fun sendSingle(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ): Map<String, Any> {
        val dataJson = if (data.isNotEmpty()) {
            ",\"data\":{${data.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }}}"
        } else {
            ""
        }
        val payload = """{"message":{"token":"$token","notification":{"title":"$title","body":"$body"}$dataJson}}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create(fcmUrl))
            .header("Authorization", "Bearer ${credentials.accessToken.tokenValue}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return if (response.statusCode() == 200) {
            mapOf("token" to token, "result" to "sent")
        } else {
            val fcmError = FcmErrorBody.from(response.body())
            val errorCode = fcmError.errorCode ?: "HTTP_${response.statusCode()}"
            logger.warn(
                "FCM HTTP error: tokenHash={}, status={}, fcmStatus={}, fcmErrorCode={}, fcmMessage={}",
                token.sha256Prefix(),
                response.statusCode(),
                fcmError.status,
                fcmError.errorCode,
                fcmError.message,
            )
            mapOf(
                "token" to token,
                "result" to "failed",
                "error_code" to errorCode,
                "error" to fcmError.summary(response.statusCode()),
            )
        }
    }

    private fun refreshAccessToken() {
        try {
            credentials.refreshIfExpired()
        } catch (exception: Exception) {
            logger.error("Failed to refresh FCM access token", exception)
            throw exception
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HttpFcmClient::class.java)
    }
}

private data class FcmErrorBody(
    val status: String?,
    val message: String?,
    val errorCode: String?,
) {
    fun summary(httpStatus: Int): String =
        listOfNotNull(
            "http_status=$httpStatus",
            status?.let { "fcm_status=$it" },
            message?.let { "fcm_message=$it" },
            errorCode?.let { "fcm_error_code=$it" },
        ).joinToString(",")

    companion object {
        private val statusRegex = Regex(""""status"\s*:\s*"([^"]+)"""")
        private val messageRegex = Regex(""""message"\s*:\s*"([^"]+)"""")
        private val errorCodeRegex = Regex(""""errorCode"\s*:\s*"([^"]+)"""")

        fun from(body: String): FcmErrorBody =
            FcmErrorBody(
                status = statusRegex.find(body)?.groupValues?.get(1),
                message = messageRegex.find(body)?.groupValues?.get(1),
                errorCode = errorCodeRegex.find(body)?.groupValues?.get(1),
            )
    }
}

private fun String.sha256Prefix(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return digest.take(12)
}
