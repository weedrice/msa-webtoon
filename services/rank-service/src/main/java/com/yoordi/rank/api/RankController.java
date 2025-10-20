package com.yoordi.rank.api;

import com.yoordi.rank.service.RankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/rank")
@Tag(name = "Rank Service", description = "Real-time content ranking API")
public class RankController {

    private final RankService rankService;
    private final List<String> allowedWindows;
    private final int aggregateMax;
    private final int aggregateReadFactor;

    public RankController(RankService rankService,
                          @Value("${rank.windows}") String windowsConfig,
                          @Value("${rank.aggregateMax:5}") int aggregateMax,
                          @Value("${rank.aggregateReadFactor:3}") int aggregateReadFactor) {
        this.rankService = rankService;
        this.allowedWindows = List.of(windowsConfig.split(","))
                                   .stream().map(String::trim).toList();
        this.aggregateMax = aggregateMax;
        this.aggregateReadFactor = aggregateReadFactor;
    }

    @GetMapping("/top")
    @Operation(summary = "Get top content IDs", description = "Returns ranked list of content IDs for specified time window")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved rankings"),
            @ApiResponse(responseCode = "400", description = "Invalid window or n parameter")
    })
    public ResponseEntity<List<String>> getTopContentIds(
            @Parameter(description = "Time window (10s, 60s, 300s)", example = "60s")
            @RequestParam String window,
            @Parameter(description = "Number of results to return", example = "10")
            @RequestParam(defaultValue = "10") @Positive int n,
            @Parameter(description = "Aggregate last N windows", example = "1")
            @RequestParam(defaultValue = "1") @Positive int aggregate) {
        
        validateWindow(window);
        if (n <= 0) {
            throw new IllegalArgumentException("Parameter 'n' must be positive");
        }
        if (aggregate <= 0 || aggregate > aggregateMax) {
            throw new IllegalArgumentException("Parameter 'aggregate' must be between 1 and " + aggregateMax);
        }
        
        List<String> topContent = rankService.getTopContentIds(window, n, aggregate);
        HttpHeaders headers = buildAggregationHeaders(window, aggregate);
        return ResponseEntity.ok().headers(headers).body(topContent);
    }

    @GetMapping("/top/detail")
    @Operation(summary = "Get detailed top content rankings", description = "Returns ranked list with content IDs and their counts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved detailed rankings"),
            @ApiResponse(responseCode = "400", description = "Invalid window or n parameter")
    })
    public ResponseEntity<List<Map<String, Object>>> getTopContentDetails(
            @Parameter(description = "Time window (10s, 60s, 300s)", example = "60s")
            @RequestParam String window,
            @Parameter(description = "Number of results to return", example = "10")
            @RequestParam(defaultValue = "10") @Positive int n,
            @Parameter(description = "Aggregate last N windows", example = "1")
            @RequestParam(defaultValue = "1") @Positive int aggregate) {
        
        validateWindow(window);
        if (n <= 0) {
            throw new IllegalArgumentException("Parameter 'n' must be positive");
        }
        if (aggregate <= 0 || aggregate > aggregateMax) {
            throw new IllegalArgumentException("Parameter 'aggregate' must be between 1 and " + aggregateMax);
        }
        
        List<Map<String, Object>> topContentDetails = rankService.getTopContentDetails(window, n, aggregate);
        HttpHeaders headers = buildAggregationHeaders(window, aggregate);
        return ResponseEntity.ok().headers(headers).body(topContentDetails);
    }

    private HttpHeaders buildAggregationHeaders(String window, int aggregate) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Window", window);
        headers.add("X-Aggregate", String.valueOf(aggregate));
        headers.add("X-Aggregate-ReadFactor", String.valueOf(aggregateReadFactor));
        rankService.getAggregationRange(window, aggregate).ifPresent(m -> {
            headers.add("X-Aggregate-Start-Ms", String.valueOf(m.get("start")));
            headers.add("X-Aggregate-End-Ms", String.valueOf(m.get("end")));
            headers.add("X-Window-Seconds", String.valueOf(m.get("windowSec")));
        });
        return headers;
    }

    private void validateWindow(String window) {
        if (!allowedWindows.contains(window)) {
            throw new IllegalArgumentException("Invalid window value. Must be one of: " + String.join(", ", allowedWindows));
        }
    }
}
