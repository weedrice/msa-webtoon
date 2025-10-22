package com.yoordi.ingest;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(topics = {"events.page_view.v1"}, partitions = 1)
class IngestControllerIT {

    

    static WireMockServer wm;
    static com.yoordi.test.JwksTestUtil.Keys keys;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("KAFKA_BOOTSTRAP", () -> System.getProperty("spring.embedded.kafka.brokers"));
        r.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        r.add("topic.pageView", () -> "events.page_view.v1");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + wm.port() + "/.well-known/jwks.json");
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        wm = new WireMockServer(0);
        wm.start();
        keys = com.yoordi.test.JwksTestUtil.generateKeys();
        com.yoordi.test.JwksTestUtil.stubJwks(wm, keys);
    }

    @AfterAll
    static void afterAll() { wm.stop(); }

    @Test
    void postEventPublishesToKafka() throws Exception {
        String token = com.yoordi.test.JwksTestUtil.issueToken(keys, "it", "write:ingest", 300);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"eventId\":\"e1\",\"userId\":\"u1\",\"contentId\":\"w-it\",\"ts\":1730000000000,\"props\":{\"action\":\"view\"}}";
        ResponseEntity<Void> resp = rest.postForEntity("http://localhost:"+port+"/ingest/events", new HttpEntity<>(body,h), Void.class);
        Assertions.assertEquals(202, resp.getStatusCodeValue());

        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.embedded.kafka.brokers"));
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String,String> c = new KafkaConsumer<>(p, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of("events.page_view.v1"));
            var records = c.poll(Duration.ofSeconds(5));
            Assertions.assertFalse(records.isEmpty(), "expected at least one record");
        }
    }
}







