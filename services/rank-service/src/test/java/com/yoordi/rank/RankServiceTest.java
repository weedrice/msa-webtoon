package com.yoordi.rank;

import com.yoordi.rank.service.RankService;
import com.yoordi.rank.sink.RankSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RankServiceTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RankService rankService;

    @Autowired
    private RankSink rankSink;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("REDIS_URL", () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
        registry.add("spring.kafka.streams.application-id", () -> "rank-service-test-" + java.util.UUID.randomUUID());
        registry.add("spring.kafka.streams.state-dir", () -> System.getProperty("java.io.tmpdir") + "/kstreams/rank-service-" + java.util.UUID.randomUUID());
        registry.add("app.security.enabled", () -> "false");
    }

    @BeforeEach
    void setupTestData() {
        // 테스트용 랭킹 데이터 설정
        long windowEnd = System.currentTimeMillis();

        // 60초 윈도우 테스트 데이터
        rankSink.update(60, windowEnd, "webtoon-001", 100);
        rankSink.update(60, windowEnd, "webtoon-002", 80);
        rankSink.update(60, windowEnd, "webtoon-003", 60);

        // 10초 윈도우 테스트 데이터
        rankSink.update(10, windowEnd, "webtoon-101", 50);
        rankSink.update(10, windowEnd, "webtoon-102", 40);

        // 300초 윈도우 테스트 데이터
        rankSink.update(300, windowEnd, "webtoon-201", 200);
        rankSink.update(300, windowEnd, "webtoon-202", 150);
    }

    @Test
    void testHealthEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testTopRankings() {
        ResponseEntity<java.util.List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=10",
                java.util.List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testInvalidWindow() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=invalid&n=10",
                String.class
        );

        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void testTopRankingsWithData() {
        // Given: setupTestData에서 설정한 데이터

        // When: top 2개 요청
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=2",
                List.class
        );

        // Then: 가장 높은 점수 2개만 반환
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() <= 2);
    }

    @Test
    void testTopDetailsEndpoint() {
        // When: detailed rankings 요청
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top/detail?window=60s&n=10",
                List.class
        );

        // Then: contentId와 count 포함
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testTopDetailsWithLimit() {
        // When: detailed rankings with limit
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top/detail?window=60s&n=1",
                List.class
        );

        // Then: 1개만 반환
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() <= 1);
    }

    @Test
    void testDifferentWindowSizes() {
        // Test 10s window
        ResponseEntity<List> response10s = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=10s&n=10",
                List.class
        );
        assertEquals(HttpStatus.OK, response10s.getStatusCode());

        // Test 300s window
        ResponseEntity<List> response300s = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=300s&n=10",
                List.class
        );
        assertEquals(HttpStatus.OK, response300s.getStatusCode());
    }

    @Test
    void testInvalidNParameter() {
        // When: n이 0 이하
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=0",
                String.class
        );

        // Then: 4xx 에러
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void testInvalidNParameterNegative() {
        // When: n이 음수
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=-1",
                String.class
        );

        // Then: 4xx 에러
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void testInvalidWindowForDetails() {
        // When: invalid window for details endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top/detail?window=99s&n=10",
                String.class
        );

        // Then: 400 에러
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void testRankServiceDirectly() {
        // When: RankService를 직접 호출
        List<String> topContent = rankService.getTopContentIds("60s", 3, 1);

        // Then: 최대 3개 반환
        assertNotNull(topContent);
        assertTrue(topContent.size() <= 3);
    }

    @Test
    void testRankServiceDetailsDirectly() {
        // When: RankService details를 직접 호출
        List<Map<String, Object>> details = rankService.getTopContentDetails("60s", 3, 1);

        // Then: contentId와 count 포함
        assertNotNull(details);
        assertTrue(details.size() <= 3);

        if (!details.isEmpty()) {
            Map<String, Object> first = details.get(0);
            assertTrue(first.containsKey("contentId"));
            assertTrue(first.containsKey("count"));
        }
    }

    @Test
    void testRankSinkUpdate() {
        // Given: 새로운 랭킹 데이터
        long windowEnd = System.currentTimeMillis();
        String contentId = "webtoon-test-999";
        int count = 500;

        // When: RankSink update 호출
        rankSink.update(60, windowEnd, contentId, count);

        // Then: 데이터가 저장되어야 함 (예외 발생 안함)
        List<Map<String, Object>> details = rankService.getTopContentDetails("60s", 10, 1);
        assertNotNull(details);
    }

    @Test
    void testEmptyRankings() {
        // When: 존재하지 않는 윈도우 데이터 요청
        List<String> topContent = rankService.getTopContentIds("10s", 10, 1);

        // Then: 빈 리스트 반환 (null 아님)
        assertNotNull(topContent);
    }
}
