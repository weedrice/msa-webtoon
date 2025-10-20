package com.yoordi.catalog.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "security.permitAll=false")
class CatalogSecurityIT {

    static WireMockServer wm;
    static com.yoordi.rank.test.JwksTestUtil.Keys keys;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + wm.port() + "/.well-known/jwks.json");
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        wm = new WireMockServer(0); wm.start();
        keys = com.yoordi.rank.test.JwksTestUtil.generateKeys();
        com.yoordi.rank.test.JwksTestUtil.stubJwks(wm, keys);
    }

    @AfterAll static void afterAll(){ wm.stop(); }

    @Test
    void upsertRequiresScopeAndSucceedsWithToken() {
        // no token -> 401
        var reqBody = Map.of("id","w-sec-1","title","T","desc","D","tags", List.of("t"));
        ResponseEntity<Map> unauth = rest.postForEntity("http://localhost:"+port+"/catalog/upsert", reqBody, Map.class);
        assertEquals(401, unauth.getStatusCodeValue());

        // with scope write:catalog
        String tok = com.yoordi.rank.test.JwksTestUtil.issueToken(keys, "it", "write:catalog", 300);
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(tok);
        ResponseEntity<Map> ok = rest.postForEntity("http://localhost:"+port+"/catalog/upsert", new HttpEntity<>(reqBody,h), Map.class);
        assertEquals(200, ok.getStatusCodeValue());
        assertEquals("w-sec-1", ok.getBody().get("id"));
    }
}

