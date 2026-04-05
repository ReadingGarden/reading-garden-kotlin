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
    @Value("\${app.security.hs256-key:}") private val hs256Key: String,
) {
    private val signingKey: SecretKey by lazy {
        require(hs256Key.isNotBlank()) { "app.security.hs256-key must not be blank" }
        SecretKeySpec(Decoders.BASE64.decode(hs256Key), "HmacSHA256")
    }

    fun parseAccessToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
}
