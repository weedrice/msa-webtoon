package com.yoordi.search.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController 
@RequestMapping("/search")
public class SearchController {
    @Autowired 
    RestHighLevelClient os;
    
    @Value("${search.index}") 
    String index;

    @Operation(summary = "키워드 검색")
    @GetMapping
    public List<Map<String,Object>> q(@RequestParam String q, @RequestParam(defaultValue = "10") int size) throws IOException {
        var req = new SearchRequest(index).source(new SearchSourceBuilder()
            .query(QueryBuilders.multiMatchQuery(q, "title", "desc", "tags"))
            .size(size));
        var resp = os.search(req, RequestOptions.DEFAULT);
        var hits = resp.getHits().getHits();
        var out = new java.util.ArrayList<Map<String,Object>>(hits.length);
        for (var h : hits) out.add(h.getSourceAsMap());
        return out;
    }
}