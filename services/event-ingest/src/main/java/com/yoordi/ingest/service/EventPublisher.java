package com.yoordi.ingest.service;

import com.yoordi.ingest.api.dto.EventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final KafkaTemplate<String, EventDto> kt;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, EventDto> kt,
                          @Value("${topic.pageView}") String topic) {
        this.kt = kt;
        this.topic = topic;
    }

    public void publish(EventDto e) {
        // key = contentId (파티셔닝 일관성)
        kt.send(topic, e.contentId(), e);
    }
}