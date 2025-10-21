package com.yoordi.catalog;

import com.yoordi.catalog.core.CatalogRepository;
import com.yoordi.catalog.core.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CatalogServiceUnitTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CatalogRepository catalogRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void testUpsertAndFindById() {
        String id = "w-unit-001";
        String title = "Unit Test Webtoon";
        String desc = "Test description";
        List<String> tags = List.of("test", "unit");

        catalogRepository.upsert(id, title, desc, tags);

        Optional<Map<String, Object>> result = catalogService.findById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().get("id"));
        assertEquals(title, result.get().get("title"));
        assertEquals(desc, result.get().get("desc"));
        assertEquals(tags, result.get().get("tags"));
        assertNotNull(result.get().get("updatedAt"));
    }

    @Test
    void testFindByIdNotFound() {
        Optional<Map<String, Object>> result = catalogService.findById("non-existent-id");
        assertFalse(result.isPresent());
    }

    @Test
    void testUpsertUpdate() {
        String id = "w-unit-002";
        String title1 = "Original Title";
        String desc1 = "Original Description";
        List<String> tags1 = List.of("tag1");

        catalogRepository.upsert(id, title1, desc1, tags1);

        Optional<Map<String, Object>> result1 = catalogService.findById(id);
        assertTrue(result1.isPresent());
        assertEquals(title1, result1.get().get("title"));

        // Update
        String title2 = "Updated Title";
        String desc2 = "Updated Description";
        List<String> tags2 = List.of("tag2", "tag3");

        catalogRepository.upsert(id, title2, desc2, tags2);

        Optional<Map<String, Object>> result2 = catalogService.findById(id);
        assertTrue(result2.isPresent());
        assertEquals(title2, result2.get().get("title"));
        assertEquals(desc2, result2.get().get("desc"));
        assertEquals(tags2, result2.get().get("tags"));
    }

    @Test
    void testUpsertWithNullTags() {
        String id = "w-unit-003";
        String title = "No Tags Webtoon";
        String desc = "Description without tags";

        catalogRepository.upsert(id, title, desc, null);

        Optional<Map<String, Object>> result = catalogService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().get("id"));
        assertEquals(title, result.get().get("title"));
        assertEquals(desc, result.get().get("desc"));
        assertTrue(((List<?>) result.get().get("tags")).isEmpty());
    }

    @Test
    void testUpsertWithEmptyTags() {
        String id = "w-unit-004";
        String title = "Empty Tags Webtoon";
        String desc = "Description with empty tags";

        catalogRepository.upsert(id, title, desc, List.of());

        Optional<Map<String, Object>> result = catalogService.findById(id);
        assertTrue(result.isPresent());
        assertTrue(((List<?>) result.get().get("tags")).isEmpty());
    }

    @Test
    void testUpsertWithNullDescription() {
        String id = "w-unit-005";
        String title = "No Description Webtoon";

        catalogRepository.upsert(id, title, null, List.of("tag"));

        Optional<Map<String, Object>> result = catalogService.findById(id);
        assertTrue(result.isPresent());
        assertEquals("", result.get().get("desc"));
    }
}
