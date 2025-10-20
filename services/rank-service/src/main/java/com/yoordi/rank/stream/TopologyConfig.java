package com.yoordi.rank.stream;

import com.yoordi.rank.model.EventDto;
import com.yoordi.rank.sink.RankSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class TopologyConfig {

    private static final Logger logger = LoggerFactory.getLogger(TopologyConfig.class);
    
    private final RankSink rankSink;
    private final Counter redisUpdatesCounter;
    private final List<Integer> windowSeconds;

    public TopologyConfig(RankSink rankSink, 
                         MeterRegistry meterRegistry,
                         @Value("${rank.windows}") String windowsConfig) {
        this.rankSink = rankSink;
        this.redisUpdatesCounter = Counter.builder("rank_redis_updates_total")
                .description("Total number of Redis rank updates")
                .register(meterRegistry);
        this.windowSeconds = parseWindowSeconds(windowsConfig);
    }

    @Bean
    public KStream<String, EventDto> rankStream(StreamsBuilder streamsBuilder) {
        Serde<String> stringSerde = Serdes.String();

        // Configure JsonSerde with trusted packages
        Map<String, Object> serdeProps = new HashMap<>();
        serdeProps.put("spring.json.trusted.packages", "*");

        JsonSerde<EventDto> eventSerde = new JsonSerde<>(EventDto.class);
        eventSerde.configure(serdeProps, false);
        eventSerde.ignoreTypeHeaders();  // Ignore type headers and use class directly

        KStream<String, EventDto> events = streamsBuilder
                .stream("events.page_view.v1", Consumed.with(stringSerde, eventSerde));

        // Process each window configuration
        for (int windowSec : windowSeconds) {
            events.groupByKey()
                  .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(windowSec)))
                  .count()
                  .toStream()
                  .foreach((windowedKey, count) -> {
                      String contentId = windowedKey.key();
                      long windowStart = windowedKey.window().start();
                      long windowEnd = windowedKey.window().end();
                      
                      logger.info("Window closed: windowSec={}, windowEnd={}, contentId={}, count={}", 
                                windowSec, windowEnd, contentId, count);
                      
                      try {
                          rankSink.update(windowSec, windowEnd, contentId, count.intValue());
                          redisUpdatesCounter.increment();
                      } catch (Exception e) {
                          logger.error("Failed to update Redis rank: windowSec={}, windowEnd={}, contentId={}, count={}", 
                                     windowSec, windowEnd, contentId, count, e);
                      }
                  });
        }

        return events;
    }

    private List<Integer> parseWindowSeconds(String windowsConfig) {
        return Arrays.stream(windowsConfig.split(","))
                    .map(String::trim)
                    .map(this::parseWindow)
                    .toList();
    }

    private Integer parseWindow(String window) {
        if (window.endsWith("s")) {
            return Integer.parseInt(window.substring(0, window.length() - 1));
        }
        throw new IllegalArgumentException("Invalid window format: " + window);
    }
}