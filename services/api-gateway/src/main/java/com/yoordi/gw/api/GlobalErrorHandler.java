package com.yoordi.gw.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        String path = exchange != null && exchange.getRequest() != null ? exchange.getRequest().getPath().value() : "";
        String rid = exchange != null ? exchange.getRequest().getHeaders().getFirst("X-Request-Id") : null;
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getReason() != null ? ex.getReason() : "",
                "path", path,
                "requestId", rid
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex, ServerWebExchange exchange) {
        String path = exchange != null && exchange.getRequest() != null ? exchange.getRequest().getPath().value() : "";
        String rid = exchange != null ? exchange.getRequest().getHeaders().getFirst("X-Request-Id") : null;
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error",
                "path", path,
                "requestId", rid
        );
        return ResponseEntity.status(status).body(body);
    }
}

