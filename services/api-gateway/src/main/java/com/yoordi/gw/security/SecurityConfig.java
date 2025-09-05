package com.yoordi.gw.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    SecurityWebFilterChain filter(ServerHttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .authorizeExchange(a -> a
                .pathMatchers("/actuator/**", "/routes-info", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .pathMatchers("/ingest/**").hasAuthority("SCOPE_write:ingest")
                .pathMatchers("/rank/**").hasAuthority("SCOPE_read:rank")
                .pathMatchers("/catalog/**").hasAuthority("SCOPE_write:catalog")
                .pathMatchers("/search/**").hasAuthority("SCOPE_read:search")
                .anyExchange().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> {}))
            .build();
    }
}