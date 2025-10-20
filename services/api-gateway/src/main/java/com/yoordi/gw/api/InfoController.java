package com.yoordi.gw.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Gateway Info", description = "API Gateway routing information")
public class InfoController {
    
    @Operation(summary = "게이트웨이 라우트 요약", description = "현재 설정된 라우트 및 레이트 제한 정보")
    @GetMapping("/routes-info")
    public Map<String, Object> routesInfo() {
        return Map.of(
            "gateway", "msa-webtoon API Gateway",
            "routes", Map.of(
                "/ingest/**", "http://localhost:8101 (event-ingest service)",
                "/rank/**", "http://localhost:8102 (rank-service)"
            ),
            "rateLimit", Map.of(
                "type", "per IP address",
                "ingest", "200 req/sec (burst: 400)",
                "rank", "300 req/sec (burst: 600)"
            ),
            "features", new String[]{
                "Request ID correlation",
                "Response time tracking", 
                "Access logging",
                "Redis-based rate limiting",
                "CORS support"
            }
        );
    }
}