package std.nooook.readinggardenkotlin.modules.push.integration

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs

class FcmClientConfigTest {
    private val config = FcmClientConfig()

    @Test
    fun `fcmClient should fall back to noop when firebase properties are missing`() {
        val firebaseProperties = FirebaseProperties()

        val client = config.fcmClient(firebaseProperties)

        assertIs<NoopFcmClient>(client)
    }

    @Test
    fun `fcmClient should fall back to noop when firebase initialization fails`() {
        val invalidServiceAccount = Files.createTempFile("firebase-invalid", ".json")
        Files.writeString(invalidServiceAccount, """{"invalid":true}""")

        val firebaseProperties = FirebaseProperties(
            projectId = "reading-garden",
            serviceAccountFile = invalidServiceAccount.toString(),
        )

        try {
            val client = config.fcmClient(firebaseProperties)
            assertIs<NoopFcmClient>(client)
        } finally {
            Files.deleteIfExists(invalidServiceAccount)
        }
    }
}
