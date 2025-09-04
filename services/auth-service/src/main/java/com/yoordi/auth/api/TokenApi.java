package com.yoordi.auth.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class TokenApi {
    @Value("${jwt.secret}")
    String secret;
    
    @Value("${jwt.expires-min}")
    long expMin;
    
    @PostMapping("/token")
    public Map<String, Object> token(@RequestParam String sub, @RequestParam(required = false) String scope) {
        var now = java.time.Instant.now();
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var jwt = io.jsonwebtoken.Jwts.builder()
            .subject(sub)
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(now.plusSeconds(expMin * 60)))
            .claim("scope", scope == null ? "read:rank read:search" : scope)
            .signWith(key, io.jsonwebtoken.Jwts.SIG.HS256).compact();
        return java.util.Map.of("access_token", jwt, "token_type", "Bearer", "expires_in", expMin * 60);
    }
}