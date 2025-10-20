package com.yoordi.search.ingest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.common.xcontent.XContentType;
import java.util.Map;

@Component
public class CatalogUpsertConsumer {
    private static final Logger log = LoggerFactory.getLogger(CatalogUpsertConsumer.class);

    @Autowired
    RestHighLevelClient os;

    @Value("${search.index}")
    String index;

    private final Counter indexedCounter;
    private final Counter errorCounter;
    private final Counter indexFailureCounter;
    private final Timer indexLatencyTimer;

    public CatalogUpsertConsumer(MeterRegistry meterRegistry) {
        this.indexedCounter = Counter.builder("search_indexed_total")
            .description("Total number of documents indexed")
            .register(meterRegistry);
        this.errorCounter = Counter.builder("search_index_errors_total")
            .description("Total number of indexing errors")
            .register(meterRegistry);
        this.indexFailureCounter = Counter.builder("search_index_failure")
            .description("Total indexing failures after all retries")
            .register(meterRegistry);
        this.indexLatencyTimer = Timer.builder("search_index_latency")
            .description("Time taken to index a document")
            .register(meterRegistry);
    }

    /**
     * Indexes catalog upsert events to OpenSearch with retry logic.
     * Retries 3 times with exponential backoff (1s, 2s, 4s) before sending to DLT.
     */
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        dltTopicSuffix = ".dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @KafkaListener(topics = "catalog.upsert.v1", groupId = "search-indexer",
        properties = {"spring.json.value.default.type=java.util.Map"})
    public void onMessage(Map<String, Object> doc) {
        Timer.Sample sample = Timer.start();

        try {
            var id = (String) doc.get("id");
            var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc);
            var req = new IndexRequest(index).id(id).source(json, XContentType.JSON);
            os.index(req, RequestOptions.DEFAULT);

            indexedCounter.increment();
            sample.stop(indexLatencyTimer);
            log.info("Indexed document: id={}, title={}", id, doc.get("title"));

        } catch (Exception e) {
            errorCounter.increment();
            sample.stop(indexLatencyTimer);
            log.error("Failed to index document: id={}, retry will be attempted", doc.get("id"), e);
            throw new IndexingException("Indexing failed for document: " + doc.get("id"), e);
        }
    }

    /**
     * Handles messages that failed all retry attempts.
     * Logs the failure and increments failure counter.
     */
    @KafkaListener(topics = "catalog.upsert.v1.dlt", groupId = "search-indexer-dlt",
        properties = {"spring.json.value.default.type=java.util.Map"})
    public void onDltMessage(Map<String, Object> doc) {
        indexFailureCounter.increment();
        log.error("Document indexing permanently failed after all retries: id={}, title={}",
            doc.get("id"), doc.get("title"));
        // TODO: 필요 시 별도의 DLT 처리 로직 추가 (알림, 재처리 큐 등)
    }

    /**
     * Custom exception for indexing failures to trigger retry mechanism.
     */
    public static class IndexingException extends RuntimeException {
        public IndexingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
}