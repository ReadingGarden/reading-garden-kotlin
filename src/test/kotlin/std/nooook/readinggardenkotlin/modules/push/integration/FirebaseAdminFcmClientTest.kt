package std.nooook.readinggardenkotlin.modules.push.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebaseAdminFcmClientTest {
    @Test
    fun `sendToMany should map multicast responses per token`() {
        val sender = RecordingSender(
            listOf(
                listOf(
                    FcmSendResult.success("message-1"),
                    FcmSendResult.failure("UNREGISTERED", "registration token is invalid"),
                ),
            ),
        )
        val client = FirebaseAdminFcmClient(sender)

        val result = client.sendToMany(
            tokens = listOf("token-1", "token-2"),
            title = "독서가든",
            body = "알림 본문",
            data = mapOf("garden_no" to "7"),
        )

        assertEquals(
            listOf(
                mapOf("token" to "token-1", "result" to "sent", "message_id" to "message-1"),
                mapOf(
                    "token" to "token-2",
                    "result" to "failed",
                    "error_code" to "UNREGISTERED",
                    "error" to "registration token is invalid",
                ),
            ),
            result,
        )

        val message = sender.requests.single()
        assertEquals(listOf("token-1", "token-2"), message.tokens)
        assertEquals("독서가든", message.title)
        assertEquals("알림 본문", message.body)
        assertEquals(mapOf("garden_no" to "7"), message.data)
    }

    @Test
    fun `sendToMany should split tokens into firebase sized batches`() {
        val tokens = (1..501).map { "token-$it" }
        val sender = RecordingSender(
            listOf(
                List(500) { index -> FcmSendResult.success("message-${index + 1}") },
                listOf(FcmSendResult.success("message-501")),
            ),
        )
        val client = FirebaseAdminFcmClient(sender)

        val result = client.sendToMany(
            tokens = tokens,
            title = "제목",
            body = "본문",
            data = emptyMap(),
        )

        assertEquals(501, result.size)
        assertEquals(2, sender.requests.size)
        assertEquals(500, sender.requests[0].tokens.size)
        assertEquals(1, sender.requests[1].tokens.size)
        assertEquals("token-1", result.first()["token"])
        assertEquals("token-501", result.last()["token"])
        assertTrue(result.all { it["result"] == "sent" })
    }

    private class RecordingSender(
        private val responsesByCall: List<List<FcmSendResult>>,
    ) : FirebaseMessagingSender {
        val requests = mutableListOf<FcmMulticastRequest>()

        override fun sendEachForMulticast(request: FcmMulticastRequest): List<FcmSendResult> {
            requests += request
            return responsesByCall[requests.lastIndex]
        }
    }
}
