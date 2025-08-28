package com.yoordi.ingest;

import com.yoordi.ingest.api.dto.EventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventIngestIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void testSingleEventIngest() throws Exception {
        EventDto event = new EventDto(
                "e1",
                "u1",
                "w-777",
                System.currentTimeMillis(),
                new EventDto.Props("view")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EventDto> request = new HttpEntity<>(event, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                request,
                Void.class
        );

        assertEquals(202, response.getStatusCode().value());

        EventDto consumedEvent = consumeMessage();
        assertNotNull(consumedEvent);
        assertEquals("e1", consumedEvent.eventId());
        assertEquals("w-777", consumedEvent.contentId());
        assertEquals("view", consumedEvent.props().action());
    }

    @Test
    void testBatchEventIngest() throws Exception {
        List<EventDto> events = List.of(
                new EventDto("e2", "u2", "w-888", System.currentTimeMillis(), new EventDto.Props("like")),
                new EventDto("e3", "u3", "w-999", System.currentTimeMillis(), new EventDto.Props("view"))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<EventDto>> request = new HttpEntity<>(events, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events/batch",
                request,
                Map.class
        );

        assertEquals(202, response.getStatusCode().value());
        assertEquals(2, response.getBody().get("accepted"));

        EventDto event1 = consumeMessage();
        EventDto event2 = consumeMessage();
        assertNotNull(event1);
        assertNotNull(event2);
    }

    @Test
    void testValidationError() {
        EventDto invalidEvent = new EventDto(
                "",
                "u1",
                "",
                -1,
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EventDto> request = new HttpEntity<>(invalidEvent, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/events",
                request,
                Map.class
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("errors"));
    }

    private EventDto consumeMessage() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EventDto.class.getName());

        try (KafkaConsumer<String, EventDto> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("events.page_view.v1"));
            
            var records = consumer.poll(Duration.ofSeconds(10));
            if (!records.isEmpty()) {
                ConsumerRecord<String, EventDto> record = records.iterator().next();
                return record.value();
            }
            return null;
        }
    }
}