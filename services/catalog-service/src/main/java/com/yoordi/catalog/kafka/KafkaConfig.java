package com.yoordi.catalog.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, Map<String, Object>> kafkaTemplate(ProducerFactory<String, Map<String, Object>> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}