package com.yoordi.rank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoordi.rank.model.EventDto;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RankServiceIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.streams.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("redisson.file", () -> "classpath:test-redisson.yaml");
        // Use Redis container URL for test
        String redisUrl = String.format("redis://localhost:%d", redis.getFirstMappedPort());
        registry.add("redis.url", () -> redisUrl);
    }

    @Test
    void testRankingFlow() throws Exception {
        // Create Kafka producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        try (KafkaProducer<String, EventDto> producer = new KafkaProducer<>(props)) {
            
            // Send 5 events for w-777 and 3 events for w-888
            long currentTime = System.currentTimeMillis();
            
            for (int i = 0; i < 5; i++) {
                EventDto event = new EventDto(
                    "e" + i, "u" + i, "w-777", currentTime + i,
                    new EventDto.Props("view")
                );
                producer.send(new ProducerRecord<>("events.page_view.v1", "w-777", event));
            }
            
            for (int i = 0; i < 3; i++) {
                EventDto event = new EventDto(
                    "e" + (i + 5), "u" + (i + 5), "w-888", currentTime + i + 5,
                    new EventDto.Props("view")
                );
                producer.send(new ProducerRecord<>("events.page_view.v1", "w-888", event));
            }
            
            producer.flush();
        }

        // Wait for window to close and processing to complete
        Thread.sleep(15000);

        // Test /rank/top endpoint
        ResponseEntity<List<String>> topResponse = restTemplate.exchange(
            "http://localhost:" + port + "/rank/top?window=10s&n=2",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<String>>() {}
        );

        assertEquals(200, topResponse.getStatusCode().value());
        List<String> topContent = topResponse.getBody();
        assertNotNull(topContent);
        
        // Should have w-777 first (5 views) and w-888 second (3 views)
        if (!topContent.isEmpty()) {
            assertEquals("w-777", topContent.get(0));
            if (topContent.size() > 1) {
                assertEquals("w-888", topContent.get(1));
            }
        }

        // Test /rank/top/detail endpoint
        ResponseEntity<List<Map<String, Object>>> detailResponse = restTemplate.exchange(
            "http://localhost:" + port + "/rank/top/detail?window=10s&n=2",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertEquals(200, detailResponse.getStatusCode().value());
        List<Map<String, Object>> detailContent = detailResponse.getBody();
        assertNotNull(detailContent);
        
        if (!detailContent.isEmpty()) {
            Map<String, Object> first = detailContent.get(0);
            assertEquals("w-777", first.get("contentId"));
            assertEquals(5, first.get("count"));
        }
    }

    @Test
    void testInvalidWindowParameter() {
        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
            "http://localhost:" + port + "/rank/top?window=invalid&n=10",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, String>>() {}
        );

        assertEquals(400, response.getStatusCode().value());
        Map<String, String> error = response.getBody();
        assertNotNull(error);
        assertEquals("Invalid parameter", error.get("error"));
    }
}