package com.yoordi.gw.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IpKeyResolverTest {

    @Autowired
    private IpKeyResolver ipKeyResolver;

    @Test
    void testResolveWithXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .header("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> result = ipKeyResolver.resolve(exchange);

        StepVerifier.create(result)
                .expectNext("192.168.1.1")
                .verifyComplete();
    }

    @Test
    void testResolveWithoutXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> result = ipKeyResolver.resolve(exchange);

        StepVerifier.create(result)
                .expectNext("127.0.0.1")
                .verifyComplete();
    }

    @Test
    void testResolveWithEmptyXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .header("X-Forwarded-For", "")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 9090))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> result = ipKeyResolver.resolve(exchange);

        StepVerifier.create(result)
                .expectNext("10.0.0.5")
                .verifyComplete();
    }

    @Test
    void testResolveWithMultipleIpsInXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test")
                .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 9.10.11.12")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> result = ipKeyResolver.resolve(exchange);

        // Should extract the first IP
        StepVerifier.create(result)
                .expectNext("1.2.3.4")
                .verifyComplete();
    }
}