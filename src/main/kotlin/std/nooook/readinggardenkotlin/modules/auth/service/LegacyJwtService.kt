package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Service
class LegacyJwtService(
    @Value("\${app.security.hs256-key:}") private val hs256Key: String,
) {
    private lateinit var signingKey: SecretKey

    @PostConstruct
    fun init() {
        require(hs256Key.isNotBlank()) { "app.security.hs256-key must not be blank" }
        signingKey = try {
            SecretKeySpec(Decoders.BASE64.decode(hs256Key), "HmacSHA256")
        } catch (ex: Exception) {
            throw IllegalStateException("app.security.hs256-key must be valid Base64", ex)
        }
    }

    fun parseAccessToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
}
