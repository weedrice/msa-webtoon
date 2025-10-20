package com.yoordi.search.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.suggest.SuggestBuilder;
import org.opensearch.search.suggest.SuggestBuilders;
import org.opensearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.util.ArrayList;
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
    public Map<String, Object> search(
            @Parameter(description = "검색 키워드") @RequestParam String q,
            @Parameter(description = "태그 필터 (쉼표 구분)") @RequestParam(required = false) String tags,
            @Parameter(description = "정렬 필드 (title, updatedAt)") @RequestParam(defaultValue = "_score") String sort,
            @Parameter(description = "정렬 방향 (asc, desc)") @RequestParam(defaultValue = "desc") String order,
            @Parameter(description = "결과 개수") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "페이지 오프셋") @RequestParam(defaultValue = "0") int from,
            @Parameter(description = "하이라이트 활성화") @RequestParam(defaultValue = "false") boolean highlight
    ) throws IOException {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.multiMatchQuery(q, "title", "desc", "tags")
                .boost(2.0f));

        // 태그 필터 적용
        if (tags != null && !tags.isEmpty()) {
            String[] tagArray = tags.split(",");
            for (String tag : tagArray) {
                boolQuery.filter(QueryBuilders.termQuery("tags", tag.trim()));
            }
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(boolQuery)
            .size(size)
            .from(from);

        if (highlight) {
            HighlightBuilder hb = new HighlightBuilder()
                    .preTags("<em>")
                    .postTags("</em>");
            hb.field(new HighlightBuilder.Field("title"));
            hb.field(new HighlightBuilder.Field("desc"));
            sourceBuilder.highlighter(hb);
        }

        // 정렬 적용
        if (!"_score".equals(sort)) {
            SortOrder sortOrder = "asc".equalsIgnoreCase(order) ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(sort, sortOrder);
        }

        var req = new SearchRequest(index).source(sourceBuilder);
        var resp = os.search(req, RequestOptions.DEFAULT);
        var hits = resp.getHits().getHits();

        var results = new ArrayList<Map<String, Object>>(hits.length);
        for (var h : hits) {
            var source = h.getSourceAsMap();
            source.put("_score", h.getScore());
            if (highlight && h.getHighlightFields() != null && !h.getHighlightFields().isEmpty()) {
                var hl = new java.util.HashMap<String, Object>();
                h.getHighlightFields().forEach((k, v) -> {
                    var frags = v.getFragments();
                    if (frags != null && frags.length > 0) {
                        hl.put(k, java.util.Arrays.stream(frags).map(Object::toString).toArray());
                    }
                });
                if (!hl.isEmpty()) source.put("highlight", hl);
            }
            results.add(source);
        }

        return Map.of(
            "total", resp.getHits().getTotalHits().value,
            "results", results
        );
    }

    @Operation(summary = "자동완성 제안")
    @GetMapping("/suggest")
    public List<String> suggest(
            @Parameter(description = "검색 키워드") @RequestParam String q,
            @Parameter(description = "제안 개수") @RequestParam(defaultValue = "5") int size
    ) throws IOException {
        CompletionSuggestionBuilder suggestionBuilder = SuggestBuilders
            .completionSuggestion("title.completion")
            .prefix(q)
            .size(size)
            .skipDuplicates(true);

        SuggestBuilder suggestBuilder = new SuggestBuilder()
            .addSuggestion("title-suggest", suggestionBuilder);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .suggest(suggestBuilder);

        var req = new SearchRequest(index).source(sourceBuilder);
        var resp = os.search(req, RequestOptions.DEFAULT);

        var suggestions = new ArrayList<String>();
        var suggest = resp.getSuggest();
        if (suggest != null) {
            var titleSuggest = suggest.getSuggestion("title-suggest");
            if (titleSuggest != null) {
                titleSuggest.getEntries().forEach(entry -> {
                    entry.getOptions().forEach(option -> {
                        suggestions.add(option.getText().string());
                    });
                });
            }
        }

        return suggestions;
    }

    @Operation(summary = "태그 기반 검색")
    @GetMapping("/by-tag/{tag}")
    public List<Map<String, Object>> searchByTag(
            @Parameter(description = "태그") @PathVariable String tag,
            @Parameter(description = "결과 개수") @RequestParam(defaultValue = "10") int size
    ) throws IOException {
        var req = new SearchRequest(index).source(new SearchSourceBuilder()
            .query(QueryBuilders.termQuery("tags", tag))
            .size(size));

        var resp = os.search(req, RequestOptions.DEFAULT);
        var hits = resp.getHits().getHits();
        var results = new ArrayList<Map<String, Object>>(hits.length);
        for (var h : hits) results.add(h.getSourceAsMap());

        return results;
    }
}
