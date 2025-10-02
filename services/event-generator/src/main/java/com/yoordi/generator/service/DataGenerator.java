package com.yoordi.generator.service;

import com.yoordi.generator.config.GeneratorProperties;
import com.yoordi.generator.domain.EventDto;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DataGenerator {

    private final GeneratorProperties props;

    public DataGenerator(GeneratorProperties props) {
        this.props = props;
    }

    public EventDto generateEvent() {
        String eventId = UUID.randomUUID().toString();
        String userId = generateUserId();
        String contentId = generateContentId();
        long ts = System.currentTimeMillis();
        String action = selectAction();

        return new EventDto(
                eventId,
                userId,
                contentId,
                ts,
                new EventDto.Props(action)
        );
    }

    private String generateUserId() {
        int id = ThreadLocalRandom.current().nextInt(1, props.getUserPoolSize() + 1);
        return "u-" + id;
    }

    private String generateContentId() {
        int id = ThreadLocalRandom.current().nextInt(1, props.getContentPoolSize() + 1);
        return "w-" + id;
    }

    private String selectAction() {
        double random = ThreadLocalRandom.current().nextDouble();
        return random < props.getViewProbability() ? "view" : "like";
    }
}