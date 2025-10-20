package com.yoordi.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.yoordi.auth.keys.KeyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final KeyService keyService;

    public JwksController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        JWKSet set = keyService.jwkSet();
        return set.toJSONObject(true);
    }
}

