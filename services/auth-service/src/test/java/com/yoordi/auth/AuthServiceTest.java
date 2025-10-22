package com.yoordi.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.yoordi.auth.keys.KeyService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "app.security.enabled=false" }
)
@org.springframework.test.context.ActiveProfiles("test")
class AuthServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KeyService keyService;

    @Test
    void testTokenGeneration() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/token?sub=user123",
                null,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("access_token"));
        assertTrue(response.getBody().containsKey("token_type"));
        assertTrue(response.getBody().containsKey("expires_in"));
        assertEquals("Bearer", response.getBody().get("token_type"));
    }

    @Test
    void testTokenWithCustomScope() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/token?sub=user456&scope=admin:write",
                null,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String token = (String) response.getBody().get("access_token");
        assertNotNull(token);

        // Verify token claims
        Claims claims = Jwts.parser()
                .verifyWith(keyService.currentPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("user456", claims.getSubject());
        assertEquals("admin:write", claims.get("scope"));
    }

    @Test
    void testTokenWithDefaultScope() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/token?sub=user789",
                null,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String token = (String) response.getBody().get("access_token");
        assertNotNull(token);

        // Verify default scope
        Claims claims = Jwts.parser()
                .verifyWith(keyService.currentPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("read:rank read:search", claims.get("scope"));
    }

    @Test
    void testTokenExpiration() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/token?sub=user999",
                null,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String token = (String) response.getBody().get("access_token");
        Integer expiresIn = (Integer) response.getBody().get("expires_in");

        assertNotNull(token);
        assertNotNull(expiresIn);
        assertTrue(expiresIn > 0);

        // Verify token has valid expiration
        Claims claims = Jwts.parser()
                .verifyWith(keyService.currentPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void testTokenWithoutSubject() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/token",
                null,
                String.class
        );

        // Should return 400 Bad Request when sub parameter is missing
        // The actual status code may vary depending on Spring's validation
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void testHealthEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}



