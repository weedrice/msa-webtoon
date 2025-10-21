package com.yoordi.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
    }
)
@org.springframework.test.context.ActiveProfiles("test")
@Testcontainers
class SearchServiceTest {

    @Container
    static ElasticsearchContainer opensearch = new ElasticsearchContainer(
            DockerImageName.parse("opensearchproject/opensearch:2.12.0")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    ).withEnv("OPENSEARCH_JAVA_OPTS", "-Xms256m -Xmx256m")
     .withEnv("discovery.type", "single-node")
     .withEnv("plugins.security.disabled", "true")
     .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
     .withEnv("compatibility.override_main_response_version", "true");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("opensearch.url", () -> "http://" + opensearch.getHttpHostAddress());
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

    @Test
    void testSearch() {
        ResponseEntity<java.util.Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search?q=test&size=10",
                java.util.Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testSearchWithoutQuery() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/search",
                String.class
        );

        assertTrue(response.getStatusCode().is4xxClientError());
    }
}
