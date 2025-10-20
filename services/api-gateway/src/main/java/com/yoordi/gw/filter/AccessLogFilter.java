package com.yoordi.gw.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AccessLogFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);
    
    @Override 
    public int getOrder() { 
        return 100; // 나중에 실행
    }
    
    @Override 
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var requestId = request.getHeaders().getFirst("X-Request-Id");
        
        return chain.filter(exchange).doOnSuccess(result -> {
            var response = exchange.getResponse();
            logger.info("gw access rid={} method={} path={} status={}", 
                       requestId, 
                       request.getMethod(), 
                       request.getURI().getPath(), 
                       response.getStatusCode());
        }).doOnError(error -> {
            logger.error("gw access rid={} method={} path={} error={}", 
                        requestId, 
                        request.getMethod(), 
                        request.getURI().getPath(), 
                        error.getMessage());
        });
    }
}