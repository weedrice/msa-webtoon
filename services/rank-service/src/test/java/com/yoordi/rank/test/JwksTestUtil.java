package com.yoordi.rank.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class JwksTestUtil {
    public static class Keys {
        public final String kid;
        public final RSAPrivateKey priv;
        public final RSAPublicKey pub;
        public Keys(String kid, RSAPrivateKey priv, RSAPublicKey pub) { this.kid = kid; this.priv = priv; this.pub = pub; }
    }

    public static Keys generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String kid = java.util.UUID.randomUUID().toString();
        return new Keys(kid, (RSAPrivateKey) kp.getPrivate(), (RSAPublicKey) kp.getPublic());
    }

    public static void stubJwks(WireMockServer wm, Keys keys) throws Exception {
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(keys.pub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(keys.pub.getPublicExponent().toByteArray());
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "kid", keys.kid,
                "n", n,
                "e", e
        );
        String body = new ObjectMapper().writeValueAsString(Map.of("keys", java.util.List.of(jwk)));
        wm.stubFor(WireMock.get("/.well-known/jwks.json").willReturn(WireMock.aResponse().withHeader("Content-Type","application/json").withBody(body)));
    }

    public static String issueToken(Keys keys, String sub, String scope, long expiresSec) {
        Instant now = Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(sub)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresSec)))
                .claim("scope", scope)
                .header().add("kid", keys.kid).and()
                .signWith(keys.priv, io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }
}

