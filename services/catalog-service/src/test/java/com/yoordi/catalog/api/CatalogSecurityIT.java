package com.yoordi.catalog.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.yoordi.test.JwksTestUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "security.permitAll=false")
@Testcontainers
@org.springframework.kafka.test.context.EmbeddedKafka(topics = {"catalog.upsert.v1"}, partitions = 1)
class CatalogSecurityIT {

    static WireMockServer wm;
    static JwksTestUtil.Keys keys;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Container
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + wm.port() + "/.well-known/jwks.json");
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "false");
        r.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        r.add("KAFKA_BOOTSTRAP", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        wm = new WireMockServer(0); wm.start();
        keys = JwksTestUtil.generateKeys();
        JwksTestUtil.stubJwks(wm, keys);
    }

    @AfterAll static void afterAll(){ wm.stop(); }

    @Test
    void upsertRequiresScopeAndSucceedsWithToken() {
        // no token -> 401
        var reqBody = Map.of("id","w-sec-1","title","T","desc","D","tags", List.of("t"));
        ResponseEntity<Map> unauth = rest.postForEntity("http://localhost:"+port+"/catalog/upsert", reqBody, Map.class);
        assertEquals(401, unauth.getStatusCodeValue());

        // with scope write:catalog
        String tok = JwksTestUtil.issueToken(keys, "it", "write:catalog", 300);
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(tok);
        ResponseEntity<Map> ok = rest.postForEntity("http://localhost:"+port+"/catalog/upsert", new HttpEntity<>(reqBody,h), Map.class);
        assertEquals(200, ok.getStatusCodeValue());
        assertEquals("w-sec-1", ok.getBody().get("id"));
    }
}

