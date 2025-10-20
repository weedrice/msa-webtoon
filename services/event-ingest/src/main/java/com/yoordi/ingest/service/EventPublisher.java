package com.yoordi.ingest.service;

import com.yoordi.ingest.api.dto.EventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, EventDto> kt;
    private final String topic;
    private final Counter publishCounter;
    private final Counter publishErrorCounter;
    private final Timer publishTimer;

    public EventPublisher(KafkaTemplate<String, EventDto> kt,
                          @Value("${topic.pageView}") String topic,
                          Counter eventPublishCounter,
                          Counter eventPublishErrorCounter,
                          Timer eventPublishTimer) {
        this.kt = kt;
        this.topic = topic;
        this.publishCounter = eventPublishCounter;
        this.publishErrorCounter = eventPublishErrorCounter;
        this.publishTimer = eventPublishTimer;
    }

    @Async
    public void publish(EventDto event) {
        Timer.Sample sample = Timer.start();

        try {
            // key = contentId (파티셔닝 일관성)
            CompletableFuture<SendResult<String, EventDto>> future = kt.send(topic, event.contentId(), event);

            future.whenComplete((result, ex) -> {
                sample.stop(publishTimer);

                if (ex != null) {
                    publishErrorCounter.increment();
                    log.error("Failed to publish event: eventId={}, contentId={}, error={}",
                            event.eventId(), event.contentId(), ex.getMessage(), ex);
                } else {
                    publishCounter.increment();
                    log.debug("Event published successfully: eventId={}, contentId={}, partition={}",
                            event.eventId(), event.contentId(),
                            result.getRecordMetadata().partition());
                }
            });

        } catch (Exception ex) {
            sample.stop(publishTimer);
            publishErrorCounter.increment();
            log.error("Unexpected error publishing event: eventId={}, contentId={}",
                    event.eventId(), event.contentId(), ex);
            throw ex;
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