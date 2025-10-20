package com.yoordi.ingest.api;

import com.yoordi.ingest.api.dto.EventDto;
import com.yoordi.ingest.service.EventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ingest")
@Tag(name = "Event Ingest", description = "Event ingestion API for webtoon analytics")
public class IngestController {
    private final EventPublisher publisher;

    public IngestController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/events")
    @Operation(summary = "Ingest single event", description = "Publishes a single event to Kafka topic")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public ResponseEntity<Void> post(@RequestBody @Valid EventDto event) {
        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/events/batch")
    @Operation(summary = "Ingest batch of events", description = "Publishes multiple events to Kafka topic")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public ResponseEntity<Map<String, Integer>> postBatch(@RequestBody @NotEmpty List<@Valid EventDto> events) {
        events.forEach(publisher::publish);
        return ResponseEntity.accepted().body(Map.of("accepted", events.size()));
    }
}