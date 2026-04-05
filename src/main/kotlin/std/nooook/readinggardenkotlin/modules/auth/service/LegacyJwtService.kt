package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.SecretKey

@Service
class LegacyJwtService(
    @Value("\${app.security.hs256-key:}") private val hs256Key: String,
) {
    private lateinit var signingKey: SecretKey

    @PostConstruct
    fun init() {
        require(hs256Key.isNotBlank()) { "app.security.hs256-key must not be blank" }
        val keyBytes = try {
            Decoders.BASE64.decode(hs256Key)
        } catch (ex: Exception) {
            throw IllegalStateException("app.security.hs256-key must be valid Base64", ex)
        }
        signingKey = try {
            Keys.hmacShaKeyFor(keyBytes)
        } catch (ex: WeakKeyException) {
            throw IllegalStateException("app.security.hs256-key must decode to at least 256 bits for HS256", ex)
        }
    }

    fun parseAccessToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
}
