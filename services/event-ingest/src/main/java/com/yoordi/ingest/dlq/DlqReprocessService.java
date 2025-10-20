package com.yoordi.ingest.dlq;

import com.yoordi.ingest.api.dto.EventDto;
import com.yoordi.ingest.service.EventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DLQ Reprocessing Service
 *
 * Handles reprocessing of approved DLQ events back to the main topic.
 */
@Service
public class DlqReprocessService {

    private static final Logger log = LoggerFactory.getLogger(DlqReprocessService.class);

    private final DlqConsumerService dlqConsumerService;
    private final EventPublisher eventPublisher;
    private final Counter reprocessSuccessCounter;
    private final Counter reprocessFailureCounter;
    private final ExecutorService executorService;

    public DlqReprocessService(DlqConsumerService dlqConsumerService,
                               EventPublisher eventPublisher,
                               MeterRegistry meterRegistry) {
        this.dlqConsumerService = dlqConsumerService;
        this.eventPublisher = eventPublisher;
        this.reprocessSuccessCounter = Counter.builder("ingest.dlq.reprocess.success")
                .description("Total number of successfully reprocessed DLQ events")
                .register(meterRegistry);
        this.reprocessFailureCounter = Counter.builder("ingest.dlq.reprocess.failure")
                .description("Total number of failed DLQ reprocessing attempts")
                .register(meterRegistry);
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * Reprocess a single DLQ event
     */
    public CompletableFuture<ReprocessResult> reprocessEvent(String eventId, int partition, long offset) {
        DlqConsumerService.DlqEvent dlqEvent = dlqConsumerService.getEvent(eventId, partition, offset);

        if (dlqEvent == null) {
            log.warn("DLQ event not found for reprocessing: eventId={}", eventId);
            return CompletableFuture.completedFuture(
                    new ReprocessResult(eventId, false, "Event not found in DLQ"));
        }

        if (dlqEvent.status() != DlqConsumerService.DlqEventStatus.APPROVED) {
            log.warn("DLQ event not approved for reprocessing: eventId={}, status={}",
                    eventId, dlqEvent.status());
            return CompletableFuture.completedFuture(
                    new ReprocessResult(eventId, false, "Event not in APPROVED status"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Publish synchronously to ensure success before marking as reprocessed
                eventPublisher.publishSync(dlqEvent.event());

                dlqConsumerService.markAsReprocessed(eventId, partition, offset);
                reprocessSuccessCounter.increment();

                log.info("DLQ event reprocessed successfully: eventId={}, contentId={}",
                        eventId, dlqEvent.event().contentId());

                return new ReprocessResult(eventId, true, "Successfully reprocessed");

            } catch (Exception ex) {
                reprocessFailureCounter.increment();
                log.error("Failed to reprocess DLQ event: eventId={}", eventId, ex);
                return new ReprocessResult(eventId, false, "Failed: " + ex.getMessage());
            }
        }, executorService);
    }

    /**
     * Reprocess all approved DLQ events
     */
    public CompletableFuture<BatchReprocessResult> reprocessAllApproved() {
        List<DlqConsumerService.DlqEvent> approvedEvents = dlqConsumerService.getAllEvents().stream()
                .filter(e -> e.status() == DlqConsumerService.DlqEventStatus.APPROVED)
                .toList();

        if (approvedEvents.isEmpty()) {
            log.info("No approved DLQ events to reprocess");
            return CompletableFuture.completedFuture(
                    new BatchReprocessResult(0, 0, 0));
        }

        log.info("Starting reprocessing of {} approved DLQ events", approvedEvents.size());

        List<CompletableFuture<ReprocessResult>> futures = approvedEvents.stream()
                .map(event -> reprocessEvent(event.event().eventId(), event.partition(), event.offset()))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int total = approvedEvents.size();
                    long successful = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(ReprocessResult::success)
                            .count();
                    long failed = total - successful;

                    log.info("Batch reprocessing completed: total={}, successful={}, failed={}",
                            total, successful, failed);

                    return new BatchReprocessResult(total, (int) successful, (int) failed);
                });
    }

    /**
     * Approve and reprocess a single event
     */
    public CompletableFuture<ReprocessResult> approveAndReprocess(String eventId, int partition, long offset) {
        boolean approved = dlqConsumerService.approveEvent(eventId, partition, offset);

        if (!approved) {
            return CompletableFuture.completedFuture(
                    new ReprocessResult(eventId, false, "Failed to approve event"));
        }

        return reprocessEvent(eventId, partition, offset);
    }

    /**
     * Reprocess result for a single event
     */
    public record ReprocessResult(
            String eventId,
            boolean success,
            String message
    ) {}

    /**
     * Batch reprocess result
     */
    public record BatchReprocessResult(
            int total,
            int successful,
            int failed
    ) {}
}
