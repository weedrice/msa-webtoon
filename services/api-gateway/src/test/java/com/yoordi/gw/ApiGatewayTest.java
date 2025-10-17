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
    void testIngestRouteRequiresAuth() {
        // Routes require authentication
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                Map.of("eventId", "test", "userId", "u1", "contentId", "w1", "ts", 123456, "props", Map.of("action", "view")),
                String.class
        );

        // Should return 401 Unauthorized when no auth token is provided
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testRankRouteRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=10",
                String.class
        );

        // Should return 401 Unauthorized when no auth token is provided
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testCatalogRouteRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/w-123",
                String.class
        );

        // Should return 401 Unauthorized when no auth token is provided
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testSearchRouteRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q=test&size=10",
                String.class
        );

        // Should return 401 Unauthorized when no auth token is provided
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testActuatorEndpointAvailable() {
        // Make multiple requests to test actuator endpoint availability
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/actuator/health",
                    Map.class
            );

            // Actuator endpoint should be accessible
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }
    }

    @Test
    void testInvalidRoute() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/invalid-route",
                String.class
        );

        // Invalid route returns 401 because it requires authentication
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}