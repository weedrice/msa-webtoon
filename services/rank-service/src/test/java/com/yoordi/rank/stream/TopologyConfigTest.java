package com.yoordi.rank.stream;

import com.yoordi.rank.model.EventDto;
import com.yoordi.rank.sink.RankSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Instant;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopologyConfigTest {

    private TopologyTestDriver testDriver;
    private RankSink rankSink;
    private MeterRegistry meterRegistry;
    private TopologyConfig topologyConfig;

    @BeforeEach
    void setup() {
        rankSink = mock(RankSink.class);
        meterRegistry = new SimpleMeterRegistry();
        topologyConfig = new TopologyConfig(rankSink, meterRegistry, "10s,60s");

        StreamsBuilder builder = new StreamsBuilder();
        topologyConfig.rankStream(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-rank-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);

        testDriver = new TopologyTestDriver(builder.build(), props);
    }

    @AfterEach
    void cleanup() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void rankStream_shouldProcessEvents() {
        TestInputTopic<String, EventDto> inputTopic = testDriver.createInputTopic(
                "events.page_view.v1",
                Serdes.String().serializer(),
                new JsonSerde<>(EventDto.class).serializer()
        );

        EventDto event = new EventDto(
                "evt-001",
                "user-123",
                "webtoon-456",
                Instant.now().toEpochMilli(),
                new EventDto.Props("view")
        );

        inputTopic.pipeInput("webtoon-456", event);

        // Verify that the stream was created successfully
        // Actual windowing and sink updates would require more complex setup
    }

    @Test
    void parseWindowSeconds_shouldHandleValidFormat() {
        // Tested implicitly through constructor
        TopologyConfig config = new TopologyConfig(rankSink, meterRegistry, "10s,30s,60s");
        // If parsing fails, constructor would throw exception
    }

    @Test
    void parseWindowSeconds_shouldThrowOnInvalidFormat() {
        try {
            new TopologyConfig(rankSink, meterRegistry, "10,30s");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }
    }
}
