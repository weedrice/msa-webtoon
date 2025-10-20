package com.yoordi.rank;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.yoordi.test.JwksTestUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RankE2EIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wiremock;
    static JwksTestUtil.Keys keys;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("KAFKA_BOOTSTRAP", kafka::getBootstrapServers);
        r.add("REDIS_URL", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + wiremock.port() + "/.well-known/jwks.json");
        r.add("rank.windows", () -> "2s");
        r.add("rank.ttlFactor", () -> "1");
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        wiremock = new WireMockServer(0);
        wiremock.start();
        keys = JwksTestUtil.generateKeys();
        JwksTestUtil.stubJwks(wiremock, keys);
    }

    @AfterAll
    static void afterAll() {
        wiremock.stop();
    }

    @Test
    void rankAggregatesWindow() throws Exception {
        String topic = "events.page_view.v1";
        String contentId = "w-it-" + java.util.UUID.randomUUID().toString().substring(0,8);
        // produce few events
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName());
        try (KafkaProducer<String, String> p = new KafkaProducer<>(props)) {
            for (int i=0;i<5;i++) {
                Map<String,Object> ev = new HashMap<>();
                ev.put("eventId", "e"+i);
                ev.put("userId", "u"+i);
                ev.put("contentId", contentId);
                ev.put("ts", System.currentTimeMillis());
                ev.put("props", Map.of("action","view"));
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ev);
                p.send(new ProducerRecord<>(topic, contentId, json)).get();
            }
        }

        // wait window 2s to close and sink into redis
        Thread.sleep(2500);

        String token = JwksTestUtil.issueToken(keys, "it", "read:rank", 300);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String[]> resp = rest.exchange("http://localhost:"+port+"/rank/top?window=2s&n=50&aggregate=1",
                HttpMethod.GET, new HttpEntity<>(headers), String[].class);
        Assertions.assertEquals(200, resp.getStatusCodeValue());
        boolean found = java.util.Arrays.stream(resp.getBody()).anyMatch(s -> s.equals(contentId));
        Assertions.assertTrue(found, "expected contentId in rank top");
    }
}

