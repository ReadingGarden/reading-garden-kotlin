package std.nooook.readinggardenkotlin.modules.push.integration

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification

fun interface FirebaseMessagingSender {
    fun sendEachForMulticast(request: FcmMulticastRequest): List<FcmSendResult>
}

class FirebaseAdminMessagingSender(
    private val firebaseMessaging: FirebaseMessaging,
) : FirebaseMessagingSender {
    override fun sendEachForMulticast(request: FcmMulticastRequest): List<FcmSendResult> {
        val message = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle(request.title)
                    .setBody(request.body)
                    .build(),
            )
            .putAllData(request.data)
            .addAllTokens(request.tokens)
            .build()

        return firebaseMessaging
            .sendEachForMulticast(message)
            .responses
            .map { response ->
                if (response.isSuccessful) {
                    FcmSendResult.success(response.messageId)
                } else {
                    FcmSendResult.failure(
                        response.exception.messagingErrorCode?.name,
                        response.exception.message,
                    )
                }
            }
    }
}

data class FcmMulticastRequest(
    val tokens: List<String>,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

data class FcmSendResult(
    val successful: Boolean,
    val messageId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(messageId: String?): FcmSendResult = FcmSendResult(
            successful = true,
            messageId = messageId,
        )

        fun failure(
            errorCode: String?,
            errorMessage: String?,
        ): FcmSendResult = FcmSendResult(
            successful = false,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
    }
}
