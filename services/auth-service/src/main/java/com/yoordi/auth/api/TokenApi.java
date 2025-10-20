package com.yoordi.auth.api;

import com.yoordi.auth.keys.KeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
public class TokenApi {

    private final KeyService keyService;

    @Value("${jwt.expires-min}")
    long expMin;

    @Value("${jwt.refresh-expires-min:1440}")
    long refreshExpMin;

    public TokenApi(KeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping("/token")
    public Map<String, Object> token(@RequestParam String sub, @RequestParam(required = false) String scope) {
        var now = Instant.now();
        var accessExp = Date.from(now.plusSeconds(expMin * 60));
        var refreshExp = Date.from(now.plusSeconds(refreshExpMin * 60));

        String scopes = scope == null ? "read:rank read:search" : scope;

        String access = io.jsonwebtoken.Jwts.builder()
                .subject(sub)
                .issuedAt(Date.from(now))
                .expiration(accessExp)
                .claim("scope", scopes)
                .header().add("kid", keyService.current().kid()).and()
                .signWith((RSAPrivateKey) keyService.currentPrivateKey(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        String refresh = io.jsonwebtoken.Jwts.builder()
                .subject(sub)
                .issuedAt(Date.from(now))
                .expiration(refreshExp)
                .claim("typ", "refresh")
                .header().add("kid", keyService.current().kid()).and()
                .signWith((RSAPrivateKey) keyService.currentPrivateKey(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        return Map.of(
                "access_token", access,
                "token_type", "Bearer",
                "expires_in", expMin * 60,
                "refresh_token", refresh,
                "refresh_expires_in", refreshExpMin * 60
        );
    }

    @PostMapping("/token/refresh")
    public Map<String, Object> refresh(@RequestParam("refresh_token") String refreshToken,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam String sub) {
        // Validate refresh token signature and typ
        boolean valid = false;
        RSAPublicKey cur = keyService.currentPublicKey();
        RSAPublicKey prev = keyService.previousPublicKey();
        try {
            var jwt = io.jsonwebtoken.Jwts.parser().verifyWith(cur).build().parseSignedClaims(refreshToken);
            var claims = jwt.getPayload();
            if ("refresh".equals(claims.get("typ", String.class)) && sub.equals(claims.getSubject())) {
                valid = true;
            }
        } catch (Exception ignore) {}
        if (!valid && prev != null) {
            try {
                var jwt = io.jsonwebtoken.Jwts.parser().verifyWith(prev).build().parseSignedClaims(refreshToken);
                var claims = jwt.getPayload();
                if ("refresh".equals(claims.get("typ", String.class)) && sub.equals(claims.getSubject())) {
                    valid = true;
                }
            } catch (Exception ignore) {}
        }
        if (!valid) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }

        return token(sub, scope);
    }
}
