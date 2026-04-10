package std.nooook.readinggardenkotlin.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher
import std.nooook.readinggardenkotlin.common.security.LegacyJwtAuthenticationFilter

@Configuration
class SecurityConfig(
    private val legacyJwtAuthenticationFilter: LegacyJwtAuthenticationFilter,
    private val legacyAuthenticationEntryPoint: AuthenticationEntryPoint,
    private val legacyAccessDeniedHandler: AccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        publicEndpointRequestMatcher: RequestMatcher,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(legacyAuthenticationEntryPoint)
                it.accessDeniedHandler(legacyAccessDeniedHandler)
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(publicEndpointRequestMatcher).permitAll()
                auth.anyRequest().authenticated()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .addFilterBefore(legacyJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        // Prevent Boot from creating a generated default user while auth is not migrated yet.
        return UserDetailsService { throw UsernameNotFoundException("No local users configured") }
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
