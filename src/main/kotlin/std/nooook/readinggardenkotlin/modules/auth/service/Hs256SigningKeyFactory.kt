package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.io.Decoders
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

internal object Hs256SigningKeyFactory {
    private const val MINIMUM_HS256_KEY_BYTES = 32

    fun fromBase64(encodedKey: String): SecretKey {
        val keyBytes = try {
            Decoders.BASE64.decode(encodedKey)
        } catch (ex: Exception) {
            throw IllegalStateException("app.security.hs256-key must be valid Base64", ex)
        }

        if (keyBytes.size < MINIMUM_HS256_KEY_BYTES) {
            throw IllegalStateException("app.security.hs256-key must decode to at least 256 bits for HS256")
        }

        return SecretKeySpec(keyBytes, "HmacSHA256")
    }
}
