package com.yoordi.catalog;

import com.yoordi.catalog.api.dto.CatalogUpsertReq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void testCatalogUpsert() {
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-test-001",
                "Test Webtoon",
                "A test webtoon for unit testing",
                List.of("test", "comedy")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CatalogUpsertReq> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                entity,
                Map.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("w-test-001", response.getBody().get("id"));
    }

    @Test
    void testCatalogGet() {
        // First, insert a catalog item
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-test-002",
                "Test Webtoon 2",
                "Another test webtoon",
                List.of("test", "action")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CatalogUpsertReq> entity = new HttpEntity<>(request, headers);

        restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                entity,
                Map.class
        );

        // Then, retrieve it
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/w-test-002",
                Map.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("w-test-002", response.getBody().get("id"));
        assertEquals("Test Webtoon 2", response.getBody().get("title"));
    }

    @Test
    void testCatalogNotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/non-existent",
                Map.class
        );

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testInvalidCatalogData() {
        CatalogUpsertReq invalidRequest = new CatalogUpsertReq(
                "",  // Invalid empty ID
                "",  // Invalid empty title
                null,
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CatalogUpsertReq> entity = new HttpEntity<>(invalidRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                entity,
                Map.class
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("errors"));
    }
}