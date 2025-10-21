package com.yoordi.gw.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(value = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityWebFilterChain filter(ServerHttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(c -> {})
            .authorizeExchange(a -> a
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/actuator/**", "/routes-info", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**", "/__fallback/**").permitAll()
                .pathMatchers("/ingest/**").hasAuthority("SCOPE_write:ingest")
                .pathMatchers("/rank/**").hasAuthority("SCOPE_read:rank")
                .pathMatchers("/catalog/**").hasAuthority("SCOPE_write:catalog")
                .pathMatchers("/search/**").hasAuthority("SCOPE_read:search")
                .anyExchange().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> {}))
            .build();
    }
}
