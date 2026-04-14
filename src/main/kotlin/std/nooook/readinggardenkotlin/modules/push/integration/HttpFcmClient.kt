package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.auth.oauth2.GoogleCredentials
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
                logger.warn("FCM send failed for token {}: {}", token, exception.message)
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
            logger.warn("FCM HTTP error for token {}: status={}, body={}", token, response.statusCode(), response.body())
            mapOf(
                "token" to token,
                "result" to "failed",
                "error_code" to "HTTP_${response.statusCode()}",
                "error" to response.body(),
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
