package com.yoordi.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SearchServiceTest {

    @Container
    static ElasticsearchContainer opensearch = new ElasticsearchContainer(
            DockerImageName.parse("opensearchproject/opensearch:2.12.0")
    )
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "TestPassword123!")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("search.url", opensearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void testHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("UP"));
    }

    @Test
    void testSearchWithEmptyIndex() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q=test&size=10",
                List.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        // Empty index should return empty results
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testSearchWithInvalidParams() {
        // Test with invalid size parameter
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q=test&size=-1",
                String.class
        );

        // Should handle gracefully (either 400 or return empty results)
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError());
    }

    @Test
    void testSearchWithMissingQuery() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?size=10",
                String.class
        );

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void testSearchWithSpecialCharacters() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q={query}&size=5",
                List.class,
                "특수문자!@#$%^&*()"
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}