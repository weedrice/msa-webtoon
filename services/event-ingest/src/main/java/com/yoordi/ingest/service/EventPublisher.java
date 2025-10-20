package com.yoordi.ingest.service;

import com.yoordi.ingest.api.dto.EventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, EventDto> kt;
    private final KafkaTemplate<String, EventDto> dlqKt;
    private final String topic;
    private final String dlqTopic;
    private final Counter publishCounter;
    private final Counter publishErrorCounter;
    private final Counter dlqCounter;
    private final Timer publishTimer;
    private final Semaphore backpressureSemaphore;

    public EventPublisher(KafkaTemplate<String, EventDto> kt,
                          @Qualifier("dlqKafkaTemplate") KafkaTemplate<String, EventDto> dlqKt,
                          @Value("${topic.pageView}") String topic,
                          @Value("${topic.pageView.dlq:events.page_view.v1.dlq}") String dlqTopic,
                          @Value("${ingest.backpressure.max-concurrent:1000}") int maxConcurrent,
                          Counter eventPublishCounter,
                          Counter eventPublishErrorCounter,
                          @Value("${ingest.metrics.dlq-counter-name:ingest.events.dlq}") String dlqCounterName,
                          Timer eventPublishTimer,
                          io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.kt = kt;
        this.dlqKt = dlqKt;
        this.topic = topic;
        this.dlqTopic = dlqTopic;
        this.publishCounter = eventPublishCounter;
        this.publishErrorCounter = eventPublishErrorCounter;
        this.dlqCounter = Counter.builder(dlqCounterName)
                .description("Total number of events sent to DLQ")
                .register(meterRegistry);
        this.publishTimer = eventPublishTimer;
        this.backpressureSemaphore = new Semaphore(maxConcurrent);

        log.info("EventPublisher initialized with maxConcurrent={}, dlqTopic={}", maxConcurrent, dlqTopic);
    }

    @Async
    public void publish(EventDto event) {
        // Backpressure: Acquire permit with timeout
        boolean acquired = false;
        try {
            acquired = backpressureSemaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Backpressure limit reached, rejected event: eventId={}, contentId={}",
                        event.eventId(), event.contentId());
                publishErrorCounter.increment();
                sendToDlq(event, new IllegalStateException("Backpressure limit exceeded"));
                return;
            }

            Timer.Sample sample = Timer.start();

            // key = contentId (파티셔닝 일관성)
            CompletableFuture<SendResult<String, EventDto>> future = kt.send(topic, event.contentId(), event);

            future.whenComplete((result, ex) -> {
                sample.stop(publishTimer);
                backpressureSemaphore.release();

                if (ex != null) {
                    publishErrorCounter.increment();
                    log.error("Failed to publish event after retries: eventId={}, contentId={}, error={}",
                            event.eventId(), event.contentId(), ex.getMessage(), ex);
                    // Send to DLQ for manual review
                    sendToDlq(event, ex);
                } else {
                    publishCounter.increment();
                    log.debug("Event published successfully: eventId={}, contentId={}, partition={}",
                            event.eventId(), event.contentId(),
                            result.getRecordMetadata().partition());
                }
            });

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring permit: eventId={}, contentId={}",
                    event.eventId(), event.contentId(), ex);
            publishErrorCounter.increment();
            sendToDlq(event, ex);
        } catch (Exception ex) {
            if (acquired) {
                backpressureSemaphore.release();
            }
            publishErrorCounter.increment();
            log.error("Unexpected error publishing event: eventId={}, contentId={}",
                    event.eventId(), event.contentId(), ex);
            sendToDlq(event, ex);
            throw ex;
        }
    }

    /**
     * Send failed event to Dead Letter Queue
     */
    private void sendToDlq(EventDto event, Throwable error) {
        try {
            dlqKt.send(dlqTopic, event.contentId(), event);
            dlqCounter.increment();
            log.info("Event sent to DLQ: eventId={}, contentId={}, reason={}",
                    event.eventId(), event.contentId(), error.getMessage());
        } catch (Exception dlqEx) {
            log.error("CRITICAL: Failed to send event to DLQ: eventId={}, contentId={}",
                    event.eventId(), event.contentId(), dlqEx);
            // Last resort: log the event data for manual recovery
            log.error("Lost event data: {}", event);
        }
    }

    // Synchronous version for critical events
    public void publishSync(EventDto event) {
        Timer.Sample sample = Timer.start();

        try {
            SendResult<String, EventDto> result = kt.send(topic, event.contentId(), event).get();
            publishCounter.increment();
            sample.stop(publishTimer);

            log.debug("Event published synchronously: eventId={}, contentId={}, partition={}",
                    event.eventId(), event.contentId(),
                    result.getRecordMetadata().partition());

        } catch (Exception ex) {
            sample.stop(publishTimer);
            publishErrorCounter.increment();
            log.error("Failed to publish event synchronously: eventId={}, contentId={}",
                    event.eventId(), event.contentId(), ex);
            throw new RuntimeException("Failed to publish event", ex);
        }
    }
}