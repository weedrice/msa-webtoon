package com.yoordi.gw.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    @Test
    void getOrder_shouldReturn100() {
        assertEquals(100, filter.getOrder());
    }

    @Test
    void filter_shouldLogSuccessfulRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .header("X-Request-Id", "test-id")
                .build();

        MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(HttpStatus.OK);

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange = exchange.mutate().response(response).build();

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void filter_shouldLogErrorRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/test")
                .header("X-Request-Id", "error-id")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("Test error")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyError(RuntimeException.class);

        verify(chain).filter(any());
    }

    @Test
    void filter_withoutRequestId_shouldStillLog() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/test")
                .build();

        MockServerHttpResponse response = new MockServerHttpResponse();
        response.setStatusCode(HttpStatus.OK);

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange = exchange.mutate().response(response).build();

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }
}
