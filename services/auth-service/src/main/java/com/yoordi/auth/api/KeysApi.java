package com.yoordi.auth.api;

import com.yoordi.auth.keys.KeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KeysApi {
    private final KeyService keyService;

    public KeysApi(KeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping("/keys/rotate")
    public ResponseEntity<Map<String, String>> rotate() {
        keyService.rotate();
        return ResponseEntity.ok(Map.of("kid", keyService.current().kid()));
    }
}

