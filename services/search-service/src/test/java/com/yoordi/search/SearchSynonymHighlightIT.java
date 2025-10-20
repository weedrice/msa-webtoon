package com.yoordi.search;

import org.junit.jupiter.api.*;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.OCOpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SearchSynonymHighlightIT {

    static Network net = Network.newNetwork();

    @Container
    static OCOpenSearchContainer os = new OCOpenSearchContainer(DockerImageName.parse("opensearchproject/opensearch:2.12.0")).withEnv("plugins.security.disabled","true");

    @LocalServerPort
    int port;

    @Autowired
    RestHighLevelClient client;

    @Autowired
    TestRestTemplate rest;

    @Value("${search.index}")
    String index;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("OPENSEARCH_URL", () -> os.getHttpHostAddress());
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9/.well-known/jwks.json");
        r.add("search.bootstrap.enabled", () -> "false");
    }

    @Test
    void synonymSearchAndHighlight() throws Exception {
        // settings: simple synonym filter (no plugin), analyzers
        String settings = "{"+
                "\"analysis\": {"+
                "  \"filter\": {\"syns\": {\"type\": \"synonym_graph\", \"synonyms\": [\"웹툰, 만화\"]}},"+
                "  \"analyzer\": {\"my_syn\": {\"type\": \"custom\", \"tokenizer\": \"standard\", \"filter\": [\"lowercase\", \"syns\"]}}"+
                "}}";
        String mapping = "{"+
                "\"properties\": {"+
                "  \"id\": {\"type\": \"keyword\"},"+
                "  \"title\": {\"type\": \"text\", \"analyzer\": \"my_syn\", \"search_analyzer\": \"my_syn\"},"+
                "  \"desc\": {\"type\": \"text\", \"analyzer\": \"my_syn\", \"search_analyzer\": \"my_syn\"},"+
                "  \"tags\": {\"type\": \"keyword\"}"+
                "}}";

        var create = new CreateIndexRequest(index);
        create.settings(settings, XContentType.JSON);
        create.mapping(mapping, XContentType.JSON);
        client.indices().create(create, RequestOptions.DEFAULT);

        // index doc: title contains "웹툰"
        String id = "w-hl-" + java.util.UUID.randomUUID().toString().substring(0,8);
        String doc = "{"+
                "\"id\":\""+id+"\",\"title\":\"멋진 웹툰 작품\",\"desc\":\"하이라이트 테스트\",\"tags\":[\"t\"]"+
                "}";
        client.index(new IndexRequest(index).id(id).source(doc, XContentType.JSON), RequestOptions.DEFAULT);

        // refresh to make searchable
        client.indices().refresh(new org.opensearch.client.indices.RefreshRequest(index), RequestOptions.DEFAULT);

        // call search with synonym term "만화" and highlight=true
        HttpHeaders h = new HttpHeaders();
        // Disable JWT for this test by not requiring it (search-service allows when JWKS dead URL?)
        ResponseEntity<Map> resp = rest.getForEntity("http://localhost:"+port+"/search?q="+java.net.URLEncoder.encode("만화","UTF-8")+"&size=10&highlight=true", Map.class);
        Assertions.assertEquals(200, resp.getStatusCodeValue());
        var results = (List<Map<String,Object>>) resp.getBody().get("results");
        boolean found = results.stream().anyMatch(m -> id.equals(m.get("id")));
        Assertions.assertTrue(found, "document should be returned by synonym query");
        // check highlight exists if present
        var first = results.stream().filter(m -> id.equals(m.get("id"))).findFirst().get();
        Object hl = first.get("highlight");
        Assertions.assertNotNull(hl, "highlight should be present");
    }
}

