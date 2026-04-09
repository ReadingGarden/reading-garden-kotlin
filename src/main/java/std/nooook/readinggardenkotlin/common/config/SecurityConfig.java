package std.nooook.readinggardenkotlin.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import std.nooook.readinggardenkotlin.common.security.LegacyJwtAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private final LegacyJwtAuthenticationFilter legacyJwtAuthenticationFilter;
    private final AuthenticationEntryPoint legacyAuthenticationEntryPoint;
    private final AccessDeniedHandler legacyAccessDeniedHandler;

    public SecurityConfig(
            LegacyJwtAuthenticationFilter legacyJwtAuthenticationFilter,
            AuthenticationEntryPoint legacyAuthenticationEntryPoint,
            AccessDeniedHandler legacyAccessDeniedHandler
    ) {
        this.legacyJwtAuthenticationFilter = legacyJwtAuthenticationFilter;
        this.legacyAuthenticationEntryPoint = legacyAuthenticationEntryPoint;
        this.legacyAccessDeniedHandler = legacyAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestMatcher publicEndpointRequestMatcher
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(handling -> {
            handling.authenticationEntryPoint(this.legacyAuthenticationEntryPoint);
            handling.accessDeniedHandler(this.legacyAccessDeniedHandler);
        });
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(publicEndpointRequestMatcher).permitAll();
            auth.anyRequest().authenticated();
        });
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.addFilterBefore(this.legacyJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No local users configured");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
