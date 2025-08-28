package com.yoordi.rank.api;

import com.yoordi.rank.service.RankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rank")
@Tag(name = "Rank Service", description = "Real-time content ranking API")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
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
            @RequestParam(defaultValue = "10") @Positive int n) {
        
        validateWindow(window);
        if (n <= 0) {
            throw new IllegalArgumentException("Parameter 'n' must be positive");
        }
        
        List<String> topContent = rankService.getTopContentIds(window, n);
        return ResponseEntity.ok(topContent);
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
            @RequestParam(defaultValue = "10") @Positive int n) {
        
        validateWindow(window);
        if (n <= 0) {
            throw new IllegalArgumentException("Parameter 'n' must be positive");
        }
        
        List<Map<String, Object>> topContentDetails = rankService.getTopContentDetails(window, n);
        return ResponseEntity.ok(topContentDetails);
    }

    private void validateWindow(String window) {
        if (!List.of("10s", "60s", "300s").contains(window)) {
            throw new IllegalArgumentException("Invalid window value. Must be one of: 10s, 60s, 300s");
        }
    }
}