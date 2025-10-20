package com.yoordi.catalog.api;

import com.yoordi.catalog.api.dto.CatalogUpsertReq;
import com.yoordi.catalog.core.CatalogRepository;
import com.yoordi.catalog.core.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
    
    private final CatalogRepository repository;
    private final CatalogService service;
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
    private final String catalogUpsertTopic;

    public CatalogController(
            CatalogRepository repository,
            CatalogService service,
            KafkaTemplate<String, Map<String, Object>> kafkaTemplate,
            @Value("${topic.catalogUpsert}") String catalogUpsertTopic) {
        this.repository = repository;
        this.service = service;
        this.kafkaTemplate = kafkaTemplate;
        this.catalogUpsertTopic = catalogUpsertTopic;
    }

    @Operation(summary = "카탈로그 upsert + Kafka 발행")
    @PostMapping("/upsert")
    public ResponseEntity<?> upsert(@RequestBody @Valid CatalogUpsertReq request) {
        repository.upsert(request.id(), request.title(), request.desc(), request.tags());
        
        Map<String, Object> payload = Map.of(
            "id", request.id(),
            "title", request.title(),
            "desc", request.desc() != null ? request.desc() : "",
            "tags", request.tags() != null ? request.tags() : List.of(),
            "updatedAt", System.currentTimeMillis()
        );
        
        kafkaTemplate.send(catalogUpsertTopic, request.id(), payload);
        
        return ResponseEntity.ok(Map.of("id", request.id()));
    }

    @Operation(summary = "단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<?> getCatalog(@PathVariable String id) {
        return service.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}