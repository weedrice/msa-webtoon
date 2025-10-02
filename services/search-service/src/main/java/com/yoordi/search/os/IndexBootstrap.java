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
        // Wait for OpenSearch to be ready with retry logic
        int maxRetries = 10;
        int retryDelayMs = 2000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                var exists = os.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT);
                if (!exists) {
                    var create = new CreateIndexRequest(index);
                    create.mapping("""
                        {
                          "properties": {
                            "id": {"type": "keyword"},
                            "title": {
                              "type": "text",
                              "fields": {
                                "keyword": {"type": "keyword"},
                                "completion": {"type": "completion"}
                              }
                            },
                            "desc": {"type": "text"},
                            "tags": {"type": "keyword"},
                            "updatedAt": {"type": "date"}
                          }
                        }
                        """,
                        org.opensearch.common.xcontent.XContentType.JSON
                    );
                    os.indices().create(create, RequestOptions.DEFAULT);
                }
                return; // Success, exit
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    throw new RuntimeException("Failed to connect to OpenSearch after " + maxRetries + " retries", e);
                }
                System.out.println("OpenSearch not ready, retrying in " + retryDelayMs + "ms... (attempt " + (i + 1) + "/" + maxRetries + ")");
                Thread.sleep(retryDelayMs);
            }
        }
    }
}