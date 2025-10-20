package com.yoordi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void issuesAndRefreshesTokensAndServesJwks() {
        // issue
        ResponseEntity<Map> issue = rest.postForEntity("http://localhost:"+port+"/token?sub=it&scope=read:rank", null, Map.class);
        assertEquals(200, issue.getStatusCodeValue());
        String at = (String) issue.getBody().get("access_token");
        String rt = (String) issue.getBody().get("refresh_token");
        assertNotNull(at); assertNotNull(rt);

        // refresh
        var body = new org.springframework.util.LinkedMultiValueMap<String,String>();
        body.add("sub", "it"); body.add("refresh_token", rt);
        ResponseEntity<Map> refresh = rest.postForEntity("http://localhost:"+port+"/token/refresh", body, Map.class);
        assertEquals(200, refresh.getStatusCodeValue());
        assertNotNull(refresh.getBody().get("access_token"));

        // jwks
        ResponseEntity<Map> jwks = rest.getForEntity("http://localhost:"+port+"/.well-known/jwks.json", Map.class);
        assertEquals(200, jwks.getStatusCodeValue());
        assertTrue(((java.util.List<?>)jwks.getBody().get("keys")).size() >= 1);

        // rotate
        ResponseEntity<Map> rotate = rest.postForEntity("http://localhost:"+port+"/keys/rotate", null, Map.class);
        assertEquals(200, rotate.getStatusCodeValue());
        assertNotNull(rotate.getBody().get("kid"));
    }
}

