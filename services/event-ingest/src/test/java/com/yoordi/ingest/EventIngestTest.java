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
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.enabled=false"
        }
)
@org.springframework.test.context.ActiveProfiles("test")
@EmbeddedKafka(topics = {"events.page_view.v1"}, partitions = 1)
class EventIngestTest {

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
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("topic.pageView", () -> "events.page_view.v1");
        registry.add("topic.pageView.dlq", () -> "events.page_view.v1.dlq");
        registry.add("ingest.backpressure.max-concurrent", () -> "1000");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    @BeforeEach
    void setupConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.embedded.kafka.brokers"));
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
        if (consumer != null) consumer.close();
    }

    @Test
    void testSingleEventIngestion() {
        EventDto event = new EventDto("evt-001", "user-123", "webtoon-456", System.currentTimeMillis(), new EventDto.Props("view"));

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                event,
                Void.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void testBatchEventIngestion() {
        List<EventDto> events = List.of(
                new EventDto("evt-002", "user-124", "webtoon-789", System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-003", "user-125", "webtoon-101", System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-004", "user-126", "webtoon-102", System.currentTimeMillis(), new EventDto.Props("like"))
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events/batch",
                events,
                Map.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(3, response.getBody().get("accepted"));
    }

    @Test
    void testInvalidEvent() {
        EventDto invalidEvent = new EventDto(null, null, null, 0, null);

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
        EventDto event = new EventDto("evt-kafka-001", "user-999", "webtoon-999", System.currentTimeMillis(), new EventDto.Props("view"));
        eventPublisher.publishSync(event);

        EventDto consumedEvent = pollForEvent("evt-kafka-001", Duration.ofSeconds(10));
        assertNotNull(consumedEvent);
        assertEquals("evt-kafka-001", consumedEvent.eventId());
        assertEquals("user-999", consumedEvent.userId());
        assertEquals("webtoon-999", consumedEvent.contentId());
        assertEquals("view", consumedEvent.props().action());
    }

    @Test
    void testKafkaPublishAsyncIntegration() throws InterruptedException {
        List<EventDto> events = List.of(
                new EventDto("evt-async-001", "user-201", "webtoon-301", System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-async-002", "user-202", "webtoon-302", System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-async-003", "user-203", "webtoon-303", System.currentTimeMillis(), new EventDto.Props("like"))
        );
        for (EventDto event : events) {
            eventPublisher.publish(event);
        }
        Thread.sleep(1000);

        Set<String> receivedEventIds = new HashSet<>();
        ConsumerRecords<String, EventDto> records = consumer.poll(Duration.ofSeconds(10));
        for (ConsumerRecord<String, EventDto> record : records) {
            receivedEventIds.add(record.value().eventId());
        }
        assertTrue(receivedEventIds.contains("evt-async-001"));
        assertTrue(receivedEventIds.contains("evt-async-002"));
        assertTrue(receivedEventIds.contains("evt-async-003"));
    }

    @Test
    void testKafkaPartitioning() {
        String contentId = "webtoon-partition-test";
        List<EventDto> events = List.of(
                new EventDto("evt-part-001", "user-301", contentId, System.currentTimeMillis(), new EventDto.Props("view")),
                new EventDto("evt-part-002", "user-302", contentId, System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("evt-part-003", "user-303", contentId, System.currentTimeMillis(), new EventDto.Props("view"))
        );
        for (EventDto event : events) {
            eventPublisher.publishSync(event);
        }
        Set<Integer> partitions = new HashSet<>();
        ConsumerRecords<String, EventDto> records = consumer.poll(Duration.ofSeconds(10));
        for (ConsumerRecord<String, EventDto> record : records) {
            if (record.value().contentId().equals(contentId)) {
                partitions.add(record.partition());
            }
        }
        assertEquals(1, partitions.size());
    }

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
