package com.yoordi.search.ingest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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

    public CatalogUpsertConsumer(MeterRegistry meterRegistry) {
        this.indexedCounter = Counter.builder("search_indexed_total")
            .description("Total number of documents indexed")
            .register(meterRegistry);
        this.errorCounter = Counter.builder("search_index_errors_total")
            .description("Total number of indexing errors")
            .register(meterRegistry);
    }

    @KafkaListener(topics = "catalog.upsert.v1", groupId = "search-indexer",
        properties = {"spring.json.value.default.type=java.util.Map"})
    public void onMessage(Map<String, Object> doc) {
        try {
            var id = (String) doc.get("id");
            var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc);
            var req = new IndexRequest(index).id(id).source(json, XContentType.JSON);
            os.index(req, RequestOptions.DEFAULT);

            indexedCounter.increment();
            log.info("Indexed document: id={}, title={}", id, doc.get("title"));

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to index document: {}", doc, e);
        }
    }
}