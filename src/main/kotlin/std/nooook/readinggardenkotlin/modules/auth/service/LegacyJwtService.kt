package std.nooook.readinggardenkotlin.modules.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class LegacyJwtService(
    @Value("\${app.security.hs256-key:}") private val hs256Key: String,
    @Value("\${app.security.access-token-hours:24}") private val accessTokenHours: Long,
    @Value("\${app.security.refresh-token-days:420}") private val refreshTokenDays: Long,
) {
    private lateinit var signingKey: SecretKey

    @PostConstruct
    fun init() {
        require(hs256Key.isNotBlank()) { "app.security.hs256-key must not be blank" }
        signingKey = Hs256SigningKeyFactory.fromBase64(hs256Key)
    }

    fun parseAccessToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload

    fun parseRefreshToken(token: String): Claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload.also { claims ->
            requireTokenType(claims, REFRESH_TOKEN_TYPE)
        }

    fun generateAccessToken(user: UserEntity): String =
        createToken(user, ACCESS_TOKEN_TYPE, Duration.ofHours(accessTokenHours))

    fun generateRefreshToken(user: UserEntity): String =
        createToken(user, REFRESH_TOKEN_TYPE, Duration.ofDays(refreshTokenDays))

    fun refreshTokenExpiry(): Instant = Instant.now().plus(Duration.ofDays(refreshTokenDays))

    private fun createToken(
        user: UserEntity,
        tokenType: Int,
        duration: Duration,
    ): String {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(duration)

        return Jwts.builder()
            .claim("user_no", user.id)
            .claim("user_nick", user.nick)
            .claim("type", tokenType)
            .claim("timestamp", issuedAt.toString())
            .issuedAt(Date.from(issuedAt))
            .notBefore(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact()
    }

    private fun requireTokenType(
        claims: Claims,
        expectedType: Int,
    ) {
        val actualType = (claims["type"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing token type")
        require(actualType == expectedType) { "Invalid token type" }
    }

    companion object {
        private const val ACCESS_TOKEN_TYPE = 0
        private const val REFRESH_TOKEN_TYPE = 1
    }
}
