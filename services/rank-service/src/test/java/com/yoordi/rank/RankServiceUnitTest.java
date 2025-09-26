package com.yoordi.rank;

import com.yoordi.rank.service.RankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RankServiceUnitTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RankService rankService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        Set<String> keys = redisTemplate.keys("rank:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void testEmptyRanking() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=10",
                List.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testRankingWithData() {
        // Setup some test data in Redis
        String rankKey = "rank:60s:" + (System.currentTimeMillis() / 60000);
        redisTemplate.opsForZSet().incrementScore(rankKey, "w-123", 10.0);
        redisTemplate.opsForZSet().incrementScore(rankKey, "w-456", 5.0);
        redisTemplate.opsForZSet().incrementScore(rankKey, "w-789", 15.0);

        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=10",
                List.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void testDetailedRankingWithData() {
        // Setup some test data in Redis
        String rankKey = "rank:60s:" + (System.currentTimeMillis() / 60000);
        redisTemplate.opsForZSet().incrementScore(rankKey, "w-abc", 20.0);
        redisTemplate.opsForZSet().incrementScore(rankKey, "w-def", 8.0);

        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top/detail?window=60s&n=5",
                List.class
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testInvalidWindow() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=invalid&n=10",
                Map.class
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testInvalidN() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=-1",
                Map.class
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testZeroN() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/rank/top?window=60s&n=0",
                Map.class
        );

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void testMultipleWindows() {
        String[] windows = {"10s", "60s", "300s"};

        for (String window : windows) {
            ResponseEntity<List> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/rank/top?window=" + window + "&n=5",
                    List.class
            );

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }
    }
}