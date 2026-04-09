package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.io.Decoders
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Hs256SigningKeyFactoryTest {
    @Test
    fun `creates HmacSHA256 key from valid Base64 secret`() {
        val encodedKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

        val key = Hs256SigningKeyFactory.fromBase64(encodedKey)

        assertEquals("HmacSHA256", key.algorithm)
        assertContentEquals(Decoders.BASE64.decode(encodedKey), key.encoded)
    }

    @Test
    fun `rejects invalid Base64 secret`() {
        assertThatThrownBy {
            Hs256SigningKeyFactory.fromBase64("%%%")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("app.security.hs256-key must be valid Base64")
    }

    @Test
    fun `rejects keys shorter than 256 bits`() {
        assertThatThrownBy {
            Hs256SigningKeyFactory.fromBase64("YQ==")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("app.security.hs256-key must decode to at least 256 bits for HS256")
    }
}
