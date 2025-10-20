package com.yoordi.catalog;

import com.yoordi.catalog.api.dto.CatalogUpsertReq;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${topic.catalogUpsert}")
    private String topic;

    private KafkaConsumer<String, Map<String, Object>> consumer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setupConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
    }

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
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
    void testUpsertAndGet() {
        // Given: 카탈로그 upsert 요청
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-integration-001",
                "Integration Test Webtoon",
                "Test Description for integration",
                List.of("action", "comedy")
        );

        // When: upsert API 호출
        ResponseEntity<Map> upsertResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request,
                Map.class
        );

        // Then: 201 응답 확인
        assertEquals(HttpStatus.OK, upsertResponse.getStatusCode());
        assertEquals("w-integration-001", upsertResponse.getBody().get("id"));

        // And: GET으로 조회
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/w-integration-001",
                Map.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("w-integration-001", getResponse.getBody().get("id"));
        assertEquals("Integration Test Webtoon", getResponse.getBody().get("title"));
        assertEquals("Test Description for integration", getResponse.getBody().get("desc"));
        assertEquals(List.of("action", "comedy"), getResponse.getBody().get("tags"));
    }

    @Test
    void testGetNotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/non-existent-webtoon",
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testUpsertWithMinimalData() {
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-integration-002",
                "Minimal Webtoon",
                null,
                null
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify stored data
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/w-integration-002",
                Map.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("Minimal Webtoon", getResponse.getBody().get("title"));
        assertEquals("", getResponse.getBody().get("desc"));
        assertTrue(((List<?>) getResponse.getBody().get("tags")).isEmpty());
    }

    @Test
    void testUpsertUpdate() {
        String id = "w-integration-003";

        // Initial upsert
        CatalogUpsertReq request1 = new CatalogUpsertReq(
                id,
                "Original Title",
                "Original Description",
                List.of("tag1")
        );

        restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request1,
                Map.class
        );

        // Update
        CatalogUpsertReq request2 = new CatalogUpsertReq(
                id,
                "Updated Title",
                "Updated Description",
                List.of("tag2", "tag3")
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request2,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify update
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/" + id,
                Map.class
        );

        assertEquals("Updated Title", getResponse.getBody().get("title"));
        assertEquals("Updated Description", getResponse.getBody().get("desc"));
        assertEquals(List.of("tag2", "tag3"), getResponse.getBody().get("tags"));
    }

    @Test
    void testUpsertKafkaIntegration() {
        // Given: 카탈로그 upsert 요청
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-kafka-001",
                "Kafka Test Webtoon",
                "Testing Kafka integration",
                List.of("kafka", "test")
        );

        // When: upsert API 호출
        restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request,
                Map.class
        );

        // Then: Kafka에서 메시지 수신 확인
        Map<String, Object> consumedEvent = pollForCatalogEvent("w-kafka-001", Duration.ofSeconds(10));
        assertNotNull(consumedEvent, "Kafka에서 카탈로그 이벤트를 수신해야 함");
        assertEquals("w-kafka-001", consumedEvent.get("id"));
        assertEquals("Kafka Test Webtoon", consumedEvent.get("title"));
        assertEquals("Testing Kafka integration", consumedEvent.get("desc"));
        assertEquals(List.of("kafka", "test"), consumedEvent.get("tags"));
        assertNotNull(consumedEvent.get("updatedAt"));
    }

    @Test
    void testUpsertValidationFailure() {
        // Given: 잘못된 요청 (id가 null)
        Map<String, Object> invalidRequest = Map.of(
                "title", "Invalid Webtoon"
        );

        // When: upsert API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                invalidRequest,
                Map.class
        );

        // Then: 400 에러 응답
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testUpsertWithBlankId() {
        // Given: 빈 id
        CatalogUpsertReq request = new CatalogUpsertReq(
                "",
                "Test Webtoon",
                "Test Description",
                List.of("tag1")
        );

        // When: upsert API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request,
                Map.class
        );

        // Then: 400 에러 응답
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testUpsertWithBlankTitle() {
        // Given: 빈 title
        CatalogUpsertReq request = new CatalogUpsertReq(
                "w-test-blank-title",
                "",
                "Test Description",
                List.of("tag1")
        );

        // When: upsert API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/catalog/upsert",
                request,
                Map.class
        );

        // Then: 400 에러 응답
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testConcurrentUpserts() throws InterruptedException {
        // Given: 동일한 id에 대한 여러 upsert 요청
        String id = "w-concurrent-001";
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                CatalogUpsertReq request = new CatalogUpsertReq(
                        id,
                        "Title-" + index,
                        "Desc-" + index,
                        List.of("tag-" + index)
                );
                restTemplate.postForEntity(
                        "http://localhost:" + port + "/catalog/upsert",
                        request,
                        Map.class
                );
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: 최종 상태 확인 (마지막 업데이트가 저장됨)
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/catalog/" + id,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().get("id"));
        assertNotNull(response.getBody().get("title"));
    }

    /**
     * Helper method: Kafka에서 특정 id를 가진 카탈로그 이벤트를 poll
     */
    private Map<String, Object> pollForCatalogEvent(String id, Duration timeout) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, Map<String, Object>> records = consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, Map<String, Object>> record : records) {
                if (id.equals(record.value().get("id"))) {
                    return record.value();
                }
            }
        }

        return null;
    }
}