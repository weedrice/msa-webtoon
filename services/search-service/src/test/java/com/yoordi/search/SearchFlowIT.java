package com.yoordi.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@Testcontainers
class SearchFlowIT {

    static Network net = Network.newNetwork();

    @Container
    static ElasticsearchContainer os = new ElasticsearchContainer(
            DockerImageName.parse("opensearchproject/opensearch:2.12.0")
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
        )
        .withEnv("OPENSEARCH_JAVA_OPTS","-Xms256m -Xmx256m")
        .withEnv("discovery.type","single-node")
        .withEnv("plugins.security.disabled","true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG","true")
        .withEnv("compatibility.override_main_response_version","true")
        .withNetwork(net);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3")).withNetwork(net);

    static WireMockServer wm;
    static com.yoordi.test.JwksTestUtil.Keys keys;

    @Value("${search.index}")
    String index;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    RestHighLevelClient client;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opensearch.url", () -> "http://" + os.getHttpHostAddress());
        r.add("KAFKA_BOOTSTRAP", kafka::getBootstrapServers);
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:" + wm.port() + "/.well-known/jwks.json");
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        wm = new WireMockServer(0);
        wm.start();
        keys = com.yoordi.test.JwksTestUtil.generateKeys();
        com.yoordi.test.JwksTestUtil.stubJwks(wm, keys);
    }

    @AfterAll
    static void afterAll() { wm.stop(); }

    @Test
    void consumesUpsertAndSearches() throws Exception {
        // create simple index mapping compatible with test (no nori)
        var req = new CreateIndexRequest(index);
        req.mapping("{\n  \"properties\": {\n    \"id\": {\"type\": \"keyword\"},\n    \"title\": {\"type\": \"text\"},\n    \"desc\": {\"type\": \"text\"},\n    \"tags\": {\"type\": \"keyword\"}\n  }\n}", XContentType.JSON);
        client.indices().create(req, RequestOptions.DEFAULT);

        // produce catalog.upsert event
        String cid = "w-it-" + java.util.UUID.randomUUID().toString().substring(0,8);
        Map<String,Object> doc = Map.of("id", cid, "title","Hello Title","desc","Hello Desc","tags", java.util.List.of("t1","t2"), "updatedAt", System.currentTimeMillis());
        String json = new ObjectMapper().writeValueAsString(doc);
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName());
        try (KafkaProducer<String,String> p = new KafkaProducer<>(props)) {
            p.send(new ProducerRecord<>("catalog.upsert.v1", cid, json)).get();
        }

        // wait a bit for consumption
        Thread.sleep(1500);

        // query search
        String token = com.yoordi.test.JwksTestUtil.issueToken(keys, "it", "read:search", 300);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange("http://localhost:"+port+"/search?q=Hello&size=10", HttpMethod.GET, new org.springframework.http.HttpEntity<>(h), Map.class);
        org.junit.jupiter.api.Assertions.assertEquals(200, resp.getStatusCodeValue());
        var list = (java.util.List<Map<String,Object>>) resp.getBody().get("results");
        boolean found = list.stream().anyMatch(m -> cid.equals(m.get("id")));
        org.junit.jupiter.api.Assertions.assertTrue(found, "expected document in search results");
    }
}

