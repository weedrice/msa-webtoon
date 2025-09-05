package com.yoordi.search.os;

import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class IndexBootstrap implements CommandLineRunner {
    @Autowired 
    RestHighLevelClient os;
    
    @Value("${search.index}") 
    String index;
    
    @Override 
    public void run(String... args) throws Exception {
        var exists = os.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT);
        if (!exists) {
            var create = new CreateIndexRequest(index);
            create.mapping(
                "{\"properties\":{\"title\":{\"type\":\"text\"},\"desc\":{\"type\":\"text\"},\"tags\":{\"type\":\"keyword\"}}}", 
                org.opensearch.common.xcontent.XContentType.JSON
            );
            os.indices().create(create, RequestOptions.DEFAULT);
        }
    }
}