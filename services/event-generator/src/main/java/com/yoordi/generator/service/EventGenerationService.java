package com.yoordi.generator.service;

import com.yoordi.generator.config.GeneratorProperties;
import com.yoordi.generator.domain.EventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EventGenerationService {

    private static final Logger log = LoggerFactory.getLogger(EventGenerationService.class);

    private final DataGenerator dataGenerator;
    private final KafkaTemplate<String, EventDto> kafkaTemplate;
    private final GeneratorProperties props;
    private final String topic;

    private final Counter generatedCounter;
    private final Counter publishedCounter;
    private final Counter errorCounter;
    private final Timer publishTimer;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalGenerated = new AtomicLong(0);
    private final AtomicLong totalPublished = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    private ScheduledExecutorService scheduler;

    public EventGenerationService(DataGenerator dataGenerator,
                                   KafkaTemplate<String, EventDto> kafkaTemplate,
                                   GeneratorProperties props,
                                   @Value("${topic.pageView}") String topic,
                                   MeterRegistry registry) {
        this.dataGenerator = dataGenerator;
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
        this.topic = topic;

        this.generatedCounter = Counter.builder("generator.events.generated")
                .description("Total events generated")
                .register(registry);
        this.publishedCounter = Counter.builder("generator.events.published")
                .description("Total events published to Kafka")
                .register(registry);
        this.errorCounter = Counter.builder("generator.events.error")
                .description("Total publishing errors")
                .register(registry);
        this.publishTimer = Timer.builder("generator.publish.duration")
                .description("Event publishing duration")
                .register(registry);
    }

    public synchronized void start() {
        if (running.get()) {
            log.warn("Generator is already running");
            return;
        }

        log.info("Starting event generator: EPS={}, userPool={}, contentPool={}",
                props.getEventsPerSecond(), props.getUserPoolSize(), props.getContentPoolSize());

        running.set(true);
        scheduler = Executors.newScheduledThreadPool(2);

        // Calculate delay between events in milliseconds
        long delayMs = 1000 / props.getEventsPerSecond();

        scheduler.scheduleAtFixedRate(this::generateAndPublish, 0, delayMs, TimeUnit.MILLISECONDS);

        log.info("Event generator started successfully");
    }

    public synchronized void stop() {
        if (!running.get()) {
            log.warn("Generator is not running");
            return;
        }

        log.info("Stopping event generator...");
        running.set(false);

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Event generator stopped. Stats: generated={}, published={}, errors={}",
                totalGenerated.get(), totalPublished.get(), totalErrors.get());
    }

    private void generateAndPublish() {
        if (!running.get()) {
            return;
        }

        try {
            EventDto event = dataGenerator.generateEvent();
            generatedCounter.increment();
            totalGenerated.incrementAndGet();

            log.debug("Generating event: {}", event.eventId());

            Timer.Sample sample = Timer.start();

            CompletableFuture<SendResult<String, EventDto>> future =
                    kafkaTemplate.send(topic, event.contentId(), event);

            future.whenComplete((result, ex) -> {
                sample.stop(publishTimer);

                if (ex != null) {
                    errorCounter.increment();
                    totalErrors.incrementAndGet();
                    log.error("Failed to publish event: eventId={}, error={}",
                            event.eventId(), ex.getMessage());
                } else {
                    publishedCounter.increment();
                    totalPublished.incrementAndGet();
                    if (totalPublished.get() % 1000 == 0) {
                        log.info("Published {} events so far", totalPublished.get());
                    }
                }
            });

        } catch (Exception ex) {
            errorCounter.increment();
            totalErrors.incrementAndGet();
            log.error("Unexpected error during event generation", ex);
        }
    }

    public GeneratorStatus getStatus() {
        return new GeneratorStatus(
                running.get(),
                props.getEventsPerSecond(),
                totalGenerated.get(),
                totalPublished.get(),
                totalErrors.get()
        );
    }

    public record GeneratorStatus(
            boolean running,
            int eventsPerSecond,
            long totalGenerated,
            long totalPublished,
            long totalErrors
    ) {}
}