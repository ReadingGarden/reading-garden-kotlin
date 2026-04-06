package std.nooook.readinggardenkotlin.common.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.auth.service.LegacyJwtService

@Component
class LegacyJwtAuthenticationFilter(
    private val legacyJwtService: LegacyJwtService,
    private val userRepository: UserRepository,
    private val legacyAuthenticationEntryPoint: LegacyAuthenticationEntryPoint,
    private val publicEndpointRequestMatcher: RequestMatcher,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        publicEndpointRequestMatcher.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (authorizationHeader.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            commenceUnauthorized(
                request,
                response,
                InsufficientAuthenticationException("Missing Bearer token"),
            )
            return
        }

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authorizationHeader.removePrefix(BEARER_PREFIX).trim()
        if (token.isBlank()) {
            commenceUnauthorized(
                request,
                response,
                BadCredentialsException("Bearer token is blank"),
            )
            return
        }

        try {
            val claims = legacyJwtService.parseAccessToken(token)
            val tokenType = (claims["type"] as? Number)?.toInt()
                ?: throw BadCredentialsException("Missing token type")
            if (tokenType != ACCESS_TOKEN_TYPE) {
                throw BadCredentialsException("Invalid access token type")
            }

            val userNo = (claims["user_no"] as? Number)?.toInt()
                ?: throw BadCredentialsException("Missing user number")
            val user = userRepository.findByUserNo(userNo)
                ?: throw UsernameNotFoundException("User not found")

            val principal = LegacyAuthenticationPrincipal(
                userNo = user.userNo?.toLong() ?: userNo.toLong(),
                userNick = user.userNick,
            )

            val authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                emptyList(),
            ).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }

            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
        } catch (ex: JwtException) {
            commenceUnauthorized(request, response, BadCredentialsException("Invalid access token", ex))
        } catch (ex: AuthenticationException) {
            commenceUnauthorized(request, response, ex)
        } catch (ex: IllegalArgumentException) {
            commenceUnauthorized(request, response, BadCredentialsException("Invalid access token", ex))
        }
    }

    private fun commenceUnauthorized(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        SecurityContextHolder.clearContext()
        legacyAuthenticationEntryPoint.commence(request, response, exception)
    }

    companion object {
        private const val ACCESS_TOKEN_TYPE = 0
        private const val BEARER_PREFIX = "Bearer "
    }
}
