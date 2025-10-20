package com.yoordi.gw.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void getOrder_shouldReturnMinus100() {
        assertEquals(-100, filter.getOrder());
    }

    @Test
    void filter_withExistingRequestId_shouldPreserveIt() {
        String existingId = "existing-request-id";
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .header(RequestIdFilter.HDR, existingId)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseRequestId = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HDR);
        assertEquals(existingId, responseRequestId);
    }

    @Test
    void filter_withoutRequestId_shouldGenerateNew() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseRequestId = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HDR);
        assertNotNull(responseRequestId);
        assertFalse(responseRequestId.isBlank());
    }

    @Test
    void filter_withBlankRequestId_shouldGenerateNew() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .header(RequestIdFilter.HDR, "")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseRequestId = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HDR);
        assertNotNull(responseRequestId);
        assertFalse(responseRequestId.isBlank());
    }

    @Test
    void filter_shouldAddResponseTimeHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseTime = exchange.getResponse().getHeaders().getFirst("X-Response-Time-ms");
        assertNotNull(responseTime);
        assertTrue(Long.parseLong(responseTime) >= 0);
    }
}
