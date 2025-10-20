package com.yoordi.gw.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
    
    public static final String HDR = "X-Request-Id";
    
    @Override 
    public int getOrder() { 
        return -100; // 먼저 실행
    }
    
    @Override 
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(HDR);
        
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        
        final String finalRequestId = requestId; // final 변수로 만들어 lambda에서 사용
        
        // 응답에 Request ID 추가
        exchange.getResponse().getHeaders().add(HDR, finalRequestId);
        
        // 요청에 Request ID 추가하여 다운스트림 서비스로 전달
        var mutatedRequest = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.add(HDR, finalRequestId)))
                .build();
        
        long startTime = System.nanoTime();
        
        return chain.filter(mutatedRequest).doFinally(signalType -> {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            exchange.getResponse().getHeaders().add("X-Response-Time-ms", String.valueOf(durationMs));
        });
    }
}