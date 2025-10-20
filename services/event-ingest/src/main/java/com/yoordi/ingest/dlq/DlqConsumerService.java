package com.yoordi.ingest.dlq;

import com.yoordi.ingest.api.dto.EventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * DLQ Consumer Service
 *
 * Consumes events from the Dead Letter Queue for manual review and reprocessing.
 * Failed events can be inspected, manually approved, and republished to the main topic.
 */
@Service
public class DlqConsumerService {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumerService.class);

    private final ConcurrentMap<String, DlqEvent> dlqStore = new ConcurrentHashMap<>();
    private final Counter dlqReceivedCounter;

    public DlqConsumerService(MeterRegistry meterRegistry) {
        this.dlqReceivedCounter = Counter.builder("ingest.dlq.received")
                .description("Total number of events received from DLQ")
                .register(meterRegistry);
    }

    /**
     * Listens to DLQ topic and stores events for manual review
     */
    @KafkaListener(
            topics = "${topic.pageView.dlq}",
            groupId = "dlq-consumer-group",
            autoStartup = "false",  // Start manually via API
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDlqEvent(
            @Payload EventDto event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        String key = generateKey(event.eventId(), partition, offset);

        DlqEvent dlqEvent = new DlqEvent(
                event,
                partition,
                offset,
                timestamp,
                System.currentTimeMillis(),
                DlqEventStatus.PENDING
        );

        dlqStore.put(key, dlqEvent);
        dlqReceivedCounter.increment();

        log.info("DLQ event received and stored: eventId={}, contentId={}, partition={}, offset={}",
                event.eventId(), event.contentId(), partition, offset);

        // Manual commit after storing
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    /**
     * Get all pending DLQ events
     */
    public List<DlqEvent> getPendingEvents() {
        return dlqStore.values().stream()
                .filter(e -> e.status() == DlqEventStatus.PENDING)
                .sorted((e1, e2) -> Long.compare(e1.receivedAtMs(), e2.receivedAtMs()))
                .toList();
    }

    /**
     * Get all DLQ events (any status)
     */
    public List<DlqEvent> getAllEvents() {
        return new ArrayList<>(dlqStore.values());
    }

    /**
     * Get specific DLQ event by key
     */
    public DlqEvent getEvent(String eventId, int partition, long offset) {
        String key = generateKey(eventId, partition, offset);
        return dlqStore.get(key);
    }

    /**
     * Approve event for reprocessing
     */
    public boolean approveEvent(String eventId, int partition, long offset) {
        String key = generateKey(eventId, partition, offset);
        DlqEvent event = dlqStore.get(key);

        if (event == null) {
            log.warn("DLQ event not found: eventId={}, partition={}, offset={}",
                    eventId, partition, offset);
            return false;
        }

        if (event.status() != DlqEventStatus.PENDING) {
            log.warn("DLQ event not in PENDING status: eventId={}, status={}",
                    eventId, event.status());
            return false;
        }

        DlqEvent approved = event.withStatus(DlqEventStatus.APPROVED);
        dlqStore.put(key, approved);

        log.info("DLQ event approved for reprocessing: eventId={}, partition={}, offset={}",
                eventId, partition, offset);

        return true;
    }

    /**
     * Reject event (mark as permanently failed)
     */
    public boolean rejectEvent(String eventId, int partition, long offset) {
        String key = generateKey(eventId, partition, offset);
        DlqEvent event = dlqStore.get(key);

        if (event == null) {
            return false;
        }

        DlqEvent rejected = event.withStatus(DlqEventStatus.REJECTED);
        dlqStore.put(key, rejected);

        log.info("DLQ event rejected: eventId={}, partition={}, offset={}",
                eventId, partition, offset);

        return true;
    }

    /**
     * Mark event as reprocessed
     */
    public void markAsReprocessed(String eventId, int partition, long offset) {
        String key = generateKey(eventId, partition, offset);
        DlqEvent event = dlqStore.get(key);

        if (event != null) {
            DlqEvent reprocessed = event.withStatus(DlqEventStatus.REPROCESSED);
            dlqStore.put(key, reprocessed);
        }
    }

    /**
     * Clear all rejected and reprocessed events
     */
    public int clearProcessedEvents() {
        List<String> toRemove = dlqStore.entrySet().stream()
                .filter(e -> e.getValue().status() == DlqEventStatus.REJECTED
                          || e.getValue().status() == DlqEventStatus.REPROCESSED)
                .map(e -> e.getKey())
                .toList();

        toRemove.forEach(dlqStore::remove);

        log.info("Cleared {} processed DLQ events", toRemove.size());
        return toRemove.size();
    }

    /**
     * Get DLQ statistics
     */
    public DlqStatistics getStatistics() {
        long pending = dlqStore.values().stream()
                .filter(e -> e.status() == DlqEventStatus.PENDING).count();
        long approved = dlqStore.values().stream()
                .filter(e -> e.status() == DlqEventStatus.APPROVED).count();
        long rejected = dlqStore.values().stream()
                .filter(e -> e.status() == DlqEventStatus.REJECTED).count();
        long reprocessed = dlqStore.values().stream()
                .filter(e -> e.status() == DlqEventStatus.REPROCESSED).count();

        return new DlqStatistics(
                dlqStore.size(),
                pending,
                approved,
                rejected,
                reprocessed
        );
    }

    private String generateKey(String eventId, int partition, long offset) {
        return String.format("%s-%d-%d", eventId, partition, offset);
    }

    /**
     * DLQ Event wrapper with metadata
     */
    public record DlqEvent(
            EventDto event,
            int partition,
            long offset,
            long originalTimestamp,
            long receivedAtMs,
            DlqEventStatus status
    ) {
        public DlqEvent withStatus(DlqEventStatus newStatus) {
            return new DlqEvent(event, partition, offset, originalTimestamp, receivedAtMs, newStatus);
        }
    }

    /**
     * DLQ Event Status
     */
    public enum DlqEventStatus {
        PENDING,      // Waiting for review
        APPROVED,     // Approved for reprocessing
        REJECTED,     // Permanently rejected
        REPROCESSED   // Successfully reprocessed
    }

    /**
     * DLQ Statistics
     */
    public record DlqStatistics(
            long total,
            long pending,
            long approved,
            long rejected,
            long reprocessed
    ) {}
}
