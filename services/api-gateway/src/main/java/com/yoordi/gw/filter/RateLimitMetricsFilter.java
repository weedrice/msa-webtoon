package com.yoordi.gw.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class RateLimitMetricsFilter implements GlobalFilter, Ordered {

    private final MeterRegistry registry;

    public RateLimitMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return 200; // run late, after routing/filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doOnSuccess(unused -> {
            var status = exchange.getResponse().getStatusCode();
            if (status != null && status.value() == 429) {
                Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                String routeId = route != null ? route.getId() : "unknown";
                Counter.builder("gateway_ratelimit_rejected_total")
                        .description("Total number of requests rejected by rate limiting")
                        .tag("route", routeId)
                        .register(registry)
                        .increment();
            }
        });
    }
}

