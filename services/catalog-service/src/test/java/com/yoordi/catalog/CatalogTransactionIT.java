package com.yoordi.catalog;

import com.yoordi.catalog.api.dto.CatalogUpsertReq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.testcontainers.junit.jupiter.Testcontainers
class CatalogTransactionIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @org.testcontainers.junit.jupiter.Container
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    @org.springframework.test.context.DynamicPropertySource
    static void props(org.springframework.test.context.DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "false");
        r.add("security.permitAll", () -> "true");
    }

    @Test
    void invalidUpsertDoesNotCreateRow() {
        String id = "w-bad-" + java.util.UUID.randomUUID().toString().substring(0,8);
        // invalid: empty title
        CatalogUpsertReq bad = new CatalogUpsertReq(id, "", "desc", List.of("t"));
        ResponseEntity<Map> resp = rest.postForEntity("http://localhost:"+port+"/catalog/upsert", bad, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        // ensure not created
        ResponseEntity<Map> get = rest.getForEntity("http://localhost:"+port+"/catalog/"+id, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, get.getStatusCode());
    }
}

