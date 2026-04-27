package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.firebase.FirebaseApp
import org.slf4j.LoggerFactory
import java.security.MessageDigest

class FirebaseAdminFcmClient(
    private val firebaseMessagingSender: FirebaseMessagingSender,
    private val firebaseApp: FirebaseApp? = null,
) : FcmClient, AutoCloseable {
    override fun sendToMany(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<Map<String, Any>> {
        if (tokens.isEmpty()) {
            return emptyList()
        }

        return tokens.chunked(MAX_MULTICAST_TOKENS).flatMap { batchTokens ->
            val request = FcmMulticastRequest(
                tokens = batchTokens,
                title = title,
                body = body,
                data = data,
            )

            try {
                val responses = firebaseMessagingSender.sendEachForMulticast(request)
                batchTokens.mapIndexed { index, token ->
                    val response = responses.getOrNull(index)
                    when {
                        response == null -> failedResult(token, "UNKNOWN", "FCM response is missing")
                        response.successful -> successResult(token, response.messageId)
                        else -> {
                            logger.warn(
                                "FCM send failed: tokenHash={}, errorCode={}, message={}",
                                token.sha256Prefix(),
                                response.errorCode,
                                response.errorMessage,
                            )
                            failedResult(
                                token = token,
                                errorCode = response.errorCode ?: "UNKNOWN",
                                errorMessage = response.errorMessage ?: "FCM send failed",
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                logger.warn(
                    "FCM multicast send failed for {} tokens: {}",
                    batchTokens.size,
                    exception.message,
                    exception,
                )
                batchTokens.map { token ->
                    failedResult(
                        token = token,
                        errorCode = exception::class.simpleName ?: "EXCEPTION",
                        errorMessage = exception.message ?: "FCM multicast send failed",
                    )
                }
            }
        }
    }

    private fun successResult(
        token: String,
        messageId: String?,
    ): Map<String, Any> = buildMap {
        put("token", token)
        put("result", "sent")
        if (!messageId.isNullOrBlank()) {
            put("message_id", messageId)
        }
    }

    private fun failedResult(
        token: String,
        errorCode: String,
        errorMessage: String,
    ): Map<String, Any> = mapOf(
        "token" to token,
        "result" to "failed",
        "error_code" to errorCode,
        "error" to errorMessage,
    )

    override fun close() {
        val app = firebaseApp ?: return
        runCatching { app.delete() }
            .onFailure { exception ->
                logger.warn("Failed to delete FirebaseApp {} during shutdown", app.name, exception)
            }
    }

    companion object {
        private const val MAX_MULTICAST_TOKENS = 500
        private val logger = LoggerFactory.getLogger(FirebaseAdminFcmClient::class.java)
    }
}

private fun String.sha256Prefix(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return digest.take(12)
}

internal class NoopFcmClient : FcmClient {
    override fun sendToMany(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<Map<String, Any>> = emptyList()
}
