package com.yoordi.ingest.kafka;

import com.yoordi.ingest.api.dto.EventDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, EventDto> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Retry settings
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);  // Unlimited retries
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);       // 100ms backoff
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);   // 30s timeout
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 2min total timeout

        // Performance settings
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864); // 64MB buffer

        // Serialization
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public KafkaTemplate<String, EventDto> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * DLQ (Dead Letter Queue) KafkaTemplate
     * Sends failed events to a separate topic for manual review and reprocessing
     */
    @Bean
    public KafkaTemplate<String, EventDto> dlqKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // DLQ settings - fire and forget, no retries
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);

        // Serialization
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}


