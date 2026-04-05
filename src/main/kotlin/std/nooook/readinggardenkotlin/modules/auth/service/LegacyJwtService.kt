package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Service
class LegacyJwtService(
    @Value("\${app.security.secret-key:}") private val secretKey: String,
) {
    private val signingKey: SecretKey by lazy {
        require(secretKey.isNotBlank()) { "app.security.secret-key must not be blank" }
        SecretKeySpec(Decoders.BASE64.decode(secretKey), "HmacSHA256")
    }

    fun parseAccessToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
}
