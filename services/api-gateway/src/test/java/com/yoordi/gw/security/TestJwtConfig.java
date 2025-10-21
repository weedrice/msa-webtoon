package com.yoordi.gw.security;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

@TestConfiguration
public class TestJwtConfig {
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        return token -> Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("test"));
    }
}
