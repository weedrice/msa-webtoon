package com.yoordi.gw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiGatewayTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void testGatewayHealth() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testGatewayInfo() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/info",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("service"));
        assertEquals("api-gateway", response.getBody().get("service"));
    }

    @Test
    void testIngestRouteNotAvailable() {
        // This test assumes downstream services are not running
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                Map.of("eventId", "test", "userId", "u1", "contentId", "w1", "ts", 123456, "props", Map.of("action", "view")),
                String.class
        );

        // Should return 503 Service Unavailable when downstream service is not available
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testRankRouteNotAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=10",
                String.class
        );

        // Should return 503 Service Unavailable when downstream service is not available
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testCatalogRouteNotAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/w-123",
                String.class
        );

        // Should return 503 Service Unavailable when downstream service is not available
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testSearchRouteNotAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q=test&size=10",
                String.class
        );

        // Should return 503 Service Unavailable when downstream service is not available
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void testRateLimitingHeaders() {
        // Make multiple requests to test rate limiting
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/actuator/health",
                    Map.class
            );

            // Check for rate limiting headers
            assertTrue(response.getHeaders().containsKey("X-Request-Id") ||
                      response.getHeaders().containsKey("x-request-id"));
        }
    }

    @Test
    void testInvalidRoute() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/invalid-route",
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}