package com.yoordi.gw.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component("ipKeyResolver")
public class IpKeyResolver implements KeyResolver {
    
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        
        // X-Forwarded-For 헤더에서 실제 클라이언트 IP 추출
        String clientIp = request.getHeaders().getFirst("X-Forwarded-For");
        
        // X-Forwarded-For가 없으면 Remote Address 사용
        if (clientIp == null || clientIp.isBlank()) {
            var remoteAddress = request.getRemoteAddress();
            clientIp = remoteAddress != null ? 
                      remoteAddress.getAddress().getHostAddress() : 
                      "unknown";
        } else {
            // X-Forwarded-For는 comma-separated list일 수 있으므로 첫 번째 IP 사용
            clientIp = clientIp.split(",")[0].trim();
        }
        
        return Mono.just(clientIp);
    }
}