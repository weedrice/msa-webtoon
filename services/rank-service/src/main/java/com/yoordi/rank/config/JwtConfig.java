package com.yoordi.rank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;

@Configuration
public class JwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret:}")
    private String jwtSecret;

    // Only create an HMAC JwtDecoder when a symmetric secret is provided.
    // Otherwise, Spring Boot auto-config uses jwk-set-uri from properties.
    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.secret")
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder
            .withSecretKey(secretKeySpec)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
}
