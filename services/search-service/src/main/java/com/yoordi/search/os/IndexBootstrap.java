package com.yoordi.search.os;

import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(value = "search.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class IndexBootstrap implements CommandLineRunner {
    @Autowired
    RestHighLevelClient os;

    @Value("${search.index}")
    String index;

    private String readResource(Resource resource) throws Exception {
        try (var is = resource.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Wait for OpenSearch to be ready with retry logic
        int maxRetries = 10;
        int retryDelayMs = 2000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                var exists = os.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT);
                if (!exists) {
                    var settingsRes = new ClassPathResource("os/index-settings.json");
                    var mappingRes = new ClassPathResource("os/index-mapping.json");

                    var settingsJson = readResource(settingsRes);
                    var mappingJson = readResource(mappingRes);

                    var create = new CreateIndexRequest(index);
                    create.settings(settingsJson, XContentType.JSON);
                    create.mapping(mappingJson, XContentType.JSON);
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
