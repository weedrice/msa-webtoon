package com.yoordi.catalog;

import com.yoordi.catalog.api.dto.CatalogUpsertReq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogTransactionIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

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

