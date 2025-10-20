package com.yoordi.ingest.dlq;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * DLQ Management API
 *
 * Provides endpoints for reviewing and reprocessing events from the Dead Letter Queue.
 */
@RestController
@RequestMapping("/dlq")
@Tag(name = "DLQ Management", description = "Dead Letter Queue management and reprocessing API")
public class DlqController {

    private final DlqConsumerService dlqConsumerService;
    private final DlqReprocessService dlqReprocessService;

    public DlqController(DlqConsumerService dlqConsumerService,
                         DlqReprocessService dlqReprocessService) {
        this.dlqConsumerService = dlqConsumerService;
        this.dlqReprocessService = dlqReprocessService;
    }

    @GetMapping("/events")
    @Operation(summary = "List all DLQ events", description = "Returns all events in the DLQ with their statuses")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of DLQ events")
    })
    public ResponseEntity<List<DlqConsumerService.DlqEvent>> listAllEvents() {
        return ResponseEntity.ok(dlqConsumerService.getAllEvents());
    }

    @GetMapping("/events/pending")
    @Operation(summary = "List pending DLQ events", description = "Returns only pending events waiting for review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of pending DLQ events")
    })
    public ResponseEntity<List<DlqConsumerService.DlqEvent>> listPendingEvents() {
        return ResponseEntity.ok(dlqConsumerService.getPendingEvents());
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get specific DLQ event", description = "Returns a specific event by ID, partition, and offset")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "DLQ event found"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public ResponseEntity<DlqConsumerService.DlqEvent> getEvent(
            @PathVariable String eventId,
            @RequestParam int partition,
            @RequestParam long offset) {

        DlqConsumerService.DlqEvent event = dlqConsumerService.getEvent(eventId, partition, offset);

        if (event == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(event);
    }

    @PostMapping("/events/{eventId}/approve")
    @Operation(summary = "Approve DLQ event", description = "Mark event as approved for reprocessing")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event approved"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "400", description = "Event not in PENDING status")
    })
    public ResponseEntity<Map<String, Object>> approveEvent(
            @PathVariable String eventId,
            @Parameter(description = "Kafka partition") @RequestParam int partition,
            @Parameter(description = "Kafka offset") @RequestParam long offset) {

        boolean success = dlqConsumerService.approveEvent(eventId, partition, offset);

        if (!success) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Failed to approve event"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Event approved"));
    }

    @PostMapping("/events/{eventId}/reject")
    @Operation(summary = "Reject DLQ event", description = "Mark event as permanently rejected")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event rejected"),
            @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public ResponseEntity<Map<String, Object>> rejectEvent(
            @PathVariable String eventId,
            @RequestParam int partition,
            @RequestParam long offset) {

        boolean success = dlqConsumerService.rejectEvent(eventId, partition, offset);

        if (!success) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Event rejected"));
    }

    @PostMapping("/events/{eventId}/reprocess")
    @Operation(summary = "Reprocess DLQ event", description = "Reprocess a single approved event")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reprocessing started"),
            @ApiResponse(responseCode = "400", description = "Event not approved or not found")
    })
    public CompletableFuture<ResponseEntity<DlqReprocessService.ReprocessResult>> reprocessEvent(
            @PathVariable String eventId,
            @RequestParam int partition,
            @RequestParam long offset) {

        return dlqReprocessService.reprocessEvent(eventId, partition, offset)
                .thenApply(result -> {
                    if (result.success()) {
                        return ResponseEntity.accepted().body(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    @PostMapping("/events/{eventId}/approve-and-reprocess")
    @Operation(summary = "Approve and reprocess event", description = "Approve and immediately reprocess an event")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event approved and reprocessing started"),
            @ApiResponse(responseCode = "400", description = "Failed to approve or reprocess")
    })
    public CompletableFuture<ResponseEntity<DlqReprocessService.ReprocessResult>> approveAndReprocess(
            @PathVariable String eventId,
            @RequestParam int partition,
            @RequestParam long offset) {

        return dlqReprocessService.approveAndReprocess(eventId, partition, offset)
                .thenApply(result -> {
                    if (result.success()) {
                        return ResponseEntity.accepted().body(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    @PostMapping("/reprocess/all")
    @Operation(summary = "Reprocess all approved events", description = "Batch reprocess all approved DLQ events")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Batch reprocessing started")
    })
    public CompletableFuture<ResponseEntity<DlqReprocessService.BatchReprocessResult>> reprocessAll() {
        return dlqReprocessService.reprocessAllApproved()
                .thenApply(result -> ResponseEntity.accepted().body(result));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get DLQ statistics", description = "Returns statistics about DLQ events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "DLQ statistics")
    })
    public ResponseEntity<DlqConsumerService.DlqStatistics> getStatistics() {
        return ResponseEntity.ok(dlqConsumerService.getStatistics());
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "Clean up processed events", description = "Remove rejected and reprocessed events from memory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cleanup completed")
    })
    public ResponseEntity<Map<String, Object>> cleanup() {
        int removed = dlqConsumerService.clearProcessedEvents();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cleanup completed",
                "removedCount", removed
        ));
    }
}
