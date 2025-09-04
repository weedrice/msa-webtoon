package com.yoordi.search.ingest;

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
    @Autowired 
    RestHighLevelClient os;
    
    @Value("${search.index}") 
    String index;
    
    @KafkaListener(topics = "catalog.upsert.v1", groupId = "search-indexer",
        properties = {"spring.json.value.default.type=java.util.Map"})
    public void onMessage(Map<String,Object> doc) throws Exception {
        var id = (String) doc.get("id");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(doc);
        var req = new IndexRequest(index).id(id).source(json, XContentType.JSON);
        os.index(req, RequestOptions.DEFAULT);
    }
}