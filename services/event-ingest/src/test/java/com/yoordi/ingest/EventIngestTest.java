package com.yoordi.ingest;

import com.yoordi.ingest.api.dto.EventDto;
import com.yoordi.ingest.service.EventPublisher;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventIngestTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventPublisher eventPublisher;

    @Value("${topic.pageView}")
    private String topic;

    private KafkaConsumer<String, EventDto> consumer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EventDto.class.getName());

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
    void testSingleEventIngestion() {
        // Given: 유효한 이벤트 생성
        EventDto event = new EventDto(
                "evt-001",
                "user-123",
                "webtoon-456",
                System.currentTimeMillis(),
                new EventDto.Props("view")
        );

        // When: 이벤트 발행
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                event,
                Void.class
        );

        // Then: HTTP 202 Accepted 응답 확인
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void testBatchEventIngestion() {
        // Given: 여러 이벤트 생성
        List<EventDto> events = List.of(
                new EventDto("evt-002", "user-124", "webtoon-789", System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-003", "user-125", "webtoon-101", System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-004", "user-126", "webtoon-102", System.currentTimeMillis(), new EventDto.Props("like"))
        );

        // When: 배치 이벤트 발행
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events/batch",
                events,
                Map.class
        );

        // Then: HTTP 202 응답 및 accepted 개수 확인
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(3, response.getBody().get("accepted"));
    }

    @Test
    void testInvalidEvent() {
        EventDto invalidEvent = new EventDto(
                null,  // Invalid null eventId
                null,
                null,
                0,
                null
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                invalidEvent,
                Map.class
        );

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

    @Test
    void testKafkaPublishSyncIntegration() {
        // Given: 유효한 이벤트 생성
        EventDto event = new EventDto(
                "evt-kafka-001",
                "user-999",
                "webtoon-999",
                System.currentTimeMillis(),
                new EventDto.Props("view")
        );

        // When: publishSync로 직접 발행 (동기)
        eventPublisher.publishSync(event);

        // Then: Kafka에서 이벤트 소비 확인
        EventDto consumedEvent = pollForEvent("evt-kafka-001", Duration.ofSeconds(10));
        assertNotNull(consumedEvent, "Kafka에서 이벤트를 수신해야 함");
        assertEquals("evt-kafka-001", consumedEvent.eventId());
        assertEquals("user-999", consumedEvent.userId());
        assertEquals("webtoon-999", consumedEvent.contentId());
        assertEquals("view", consumedEvent.props().action());
    }

    @Test
    void testKafkaPublishAsyncIntegration() throws InterruptedException {
        // Given: 여러 이벤트 생성
        List<EventDto> events = List.of(
                new EventDto("evt-async-001", "user-201", "webtoon-301", System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-async-002", "user-202", "webtoon-302", System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-async-003", "user-203", "webtoon-303", System.currentTimeMillis(), new EventDto.Props("like"))
        );

        // When: publish (비동기)로 발행
        for (EventDto event : events) {
            eventPublisher.publish(event);
        }

        // 비동기 처리를 위해 약간 대기
        Thread.sleep(1000);

        // Then: Kafka에서 모든 이벤트 소비 확인
        Set<String> receivedEventIds = new HashSet<>();
        ConsumerRecords<String, EventDto> records = consumer.poll(Duration.ofSeconds(10));

        for (ConsumerRecord<String, EventDto> record : records) {
            receivedEventIds.add(record.value().eventId());
        }

        assertTrue(receivedEventIds.contains("evt-async-001"), "evt-async-001이 수신되어야 함");
        assertTrue(receivedEventIds.contains("evt-async-002"), "evt-async-002가 수신되어야 함");
        assertTrue(receivedEventIds.contains("evt-async-003"), "evt-async-003이 수신되어야 함");
    }

    @Test
    void testKafkaPartitioning() {
        // Given: 동일한 contentId를 가진 이벤트들
        String contentId = "webtoon-partition-test";
        List<EventDto> events = List.of(
                new EventDto("evt-part-001", "user-301", contentId, System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-part-002", "user-302", contentId, System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-part-003", "user-303", contentId, System.currentTimeMillis(), new EventDto.Props("view"))
        );

        // When: publishSync로 발행
        for (EventDto event : events) {
            eventPublisher.publishSync(event);
        }

        // Then: 모든 이벤트가 동일한 파티션에 배치되었는지 확인
        Set<Integer> partitions = new HashSet<>();
        ConsumerRecords<String, EventDto> records = consumer.poll(Duration.ofSeconds(10));

        for (ConsumerRecord<String, EventDto> record : records) {
            if (record.value().contentId().equals(contentId)) {
                partitions.add(record.partition());
            }
        }

        assertEquals(1, partitions.size(), "동일한 contentId는 같은 파티션에 배치되어야 함");
    }

    /**
     * Helper method: Kafka에서 특정 eventId를 가진 이벤트를 poll
     */
    private EventDto pollForEvent(String eventId, Duration timeout) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, EventDto> records = consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, EventDto> record : records) {
                if (record.value().eventId().equals(eventId)) {
                    return record.value();
                }
            }
        }

        return null;
    }
}
