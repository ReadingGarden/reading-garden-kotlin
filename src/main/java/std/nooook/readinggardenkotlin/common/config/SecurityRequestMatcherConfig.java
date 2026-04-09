package std.nooook.readinggardenkotlin.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityRequestMatcherConfig {

    @Bean
    public RequestMatcher publicEndpointRequestMatcher() {
        return new OrRequestMatcher(List.of(
                PathPatternRequestMatcher.pathPattern("/api/health"),
                PathPatternRequestMatcher.pathPattern("/v3/api-docs"),
                PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
                PathPatternRequestMatcher.pathPattern("/v3/api-docs.yaml"),
                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"),
                PathPatternRequestMatcher.pathPattern("/images/**"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/find-password"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/find-password/check"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.PUT, "/api/v1/auth/find-password/update-password")
        ));
    }
}
