package com.yoordi.gw.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/__fallback")
public class FallbackController {

    @GetMapping("/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service, org.springframework.web.server.ServerWebExchange exchange) {
        String rid = exchange != null ? exchange.getRequest().getHeaders().getFirst("X-Request-Id") : null;
        String path = exchange != null && exchange.getRequest() != null ? exchange.getRequest().getPath().value() : "";
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "message", "Upstream service unavailable or timed out",
                "service", service,
                "path", path,
                "requestId", rid
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
