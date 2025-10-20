package com.yoordi.search.os;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OsConfig {
    @Bean
    public RestHighLevelClient os(@Value("${opensearch.url}") String url) {
        var u = java.net.URI.create(url);
        return new RestHighLevelClient(
            RestClient.builder(new HttpHost(u.getHost(), u.getPort(), u.getScheme()))
        );
    }
}