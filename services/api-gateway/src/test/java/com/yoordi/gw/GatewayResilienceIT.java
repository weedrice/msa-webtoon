package com.yoordi.gw;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayResilienceIT {

    static WireMockServer backend;
    static WireMockServer jwks;
    static Keys keys;

    @LocalServerPort
    int port;

    @Autowired
    WebTestClient web;

    static class Keys {
        final String kid; final java.security.interfaces.RSAPrivateKey priv;
        Keys(String kid, java.security.interfaces.RSAPrivateKey priv) { this.kid=kid; this.priv=priv; }
    }

    static Keys generateKeys() throws Exception {
        var gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var kp = gen.generateKeyPair();
        var kid = java.util.UUID.randomUUID().toString();
        var pub = (java.security.interfaces.RSAPublicKey) kp.getPublic();
        var n = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        var e = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());
        var body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("keys", java.util.List.of(java.util.Map.of("kty","RSA","kid",kid,"n",n,"e",e))));
        return new Keys(kid, (java.security.interfaces.RSAPrivateKey) kp.getPrivate());
    }

    static String token(String kid, java.security.interfaces.RSAPrivateKey priv, String scope) {
        var now = java.time.Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject("it")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(300)))
                .claim("scope", scope)
                .header().add("kid", kid).and()
                .signWith(priv, io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("SERVICE_RANK_URL", () -> "http://localhost:" + backend.port());
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + jwks.port() + "/.well-known/jwks.json");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://example.com");
    }

    @BeforeAll
    static void setup() throws Exception {
        backend = new WireMockServer(0); backend.start();
        jwks = new WireMockServer(0); jwks.start();
        keys = generateKeys();
        // publish JWKS
        var pub = (java.security.interfaces.RSAPublicKey) java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(keys.priv.getModulus(), java.math.BigInteger.valueOf(65537)));
        // easier: reuse generateKeys JWKS generation
        var gen = java.security.KeyPairGenerator.getInstance("RSA"); gen.initialize(2048);
        var kp = gen.generateKeyPair();
        var testKeys = generateKeys();
        // Use JWKS from testKeys and sign with corresponding priv/kid
        jwks.stubFor(get("/.well-known/jwks.json").willReturn(aResponse().withHeader("Content-Type","application/json")
                .withBody(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("keys", java.util.List.of())))));
        // Instead, build JWKS directly from keys.priv's public portion
        var pubFromPriv = java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(keys.priv.getModulus(), java.math.BigInteger.valueOf(65537)));
        var n = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(((java.security.interfaces.RSAPublicKey)pubFromPriv).getModulus().toByteArray());
        var e = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(((java.security.interfaces.RSAPublicKey)pubFromPriv).getPublicExponent().toByteArray());
        var body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("keys", java.util.List.of(java.util.Map.of("kty","RSA","kid", keys.kid, "n", n, "e", e))));
        jwks.stubFor(get("/.well-known/jwks.json").willReturn(aResponse().withHeader("Content-Type","application/json").withBody(body)));
    }

    @AfterAll static void tearDown(){ backend.stop(); jwks.stop(); }

    @Test
    void fallbackOnTimeout() {
        // backend delays to trigger time limiter fallback
        backend.stubFor(get(urlPathMatching("/.*")).willReturn(aResponse().withFixedDelay(3000).withBody("ok")));
        String tok = token(keys.kid, keys.priv, "read:rank");

        web.get().uri("http://localhost:"+port+"/rank/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer "+tok)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.service").isEqualTo("rank");
    }

    @Test
    void corsPreflightForSearch() {
        web.options().uri("http://localhost:"+port+"/search")
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://example.com");
    }
}

