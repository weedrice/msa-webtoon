package com.yoordi.ingest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;

@Configuration
public class JwtConfig {
    
    @Value("${spring.security.oauth2.resourceserver.jwt.secret}")
    private String jwtSecret;
    
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            jwtSecret.getBytes(), 
            "HmacSHA256"
        );
        
        return NimbusJwtDecoder
            .withSecretKey(secretKeySpec)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
}