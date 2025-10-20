package com.yoordi.catalog.api;

import com.yoordi.catalog.core.CatalogRepository;
import com.yoordi.catalog.core.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogRepository repository;

    @MockBean
    private CatalogService service;

    @MockBean
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Test
    void upsert_shouldReturnOk() throws Exception {
        String requestBody = """
            {
                "id": "w-001",
                "title": "Test Webtoon",
                "desc": "Test description",
                "tags": ["action", "drama"]
            }
            """;

        when(kafkaTemplate.send(anyString(), anyString(), anyMap()))
                .thenReturn(null);

        mockMvc.perform(post("/catalog/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("w-001"));

        verify(repository).upsert(eq("w-001"), eq("Test Webtoon"), eq("Test description"), eq(List.of("action", "drama")));
        verify(kafkaTemplate).send(anyString(), eq("w-001"), anyMap());
    }

    @Test
    void upsert_withNullDesc_shouldReturnOk() throws Exception {
        String requestBody = """
            {
                "id": "w-002",
                "title": "Test Webtoon",
                "tags": ["comedy"]
            }
            """;

        when(kafkaTemplate.send(anyString(), anyString(), anyMap()))
                .thenReturn(null);

        mockMvc.perform(post("/catalog/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(repository).upsert(eq("w-002"), eq("Test Webtoon"), isNull(), eq(List.of("comedy")));
    }

    @Test
    void upsert_withBlankId_shouldReturnBadRequest() throws Exception {
        String requestBody = """
            {
                "id": "",
                "title": "Test Webtoon"
            }
            """;

        mockMvc.perform(post("/catalog/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(repository, never()).upsert(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void upsert_withBlankTitle_shouldReturnBadRequest() throws Exception {
        String requestBody = """
            {
                "id": "w-003",
                "title": ""
            }
            """;

        mockMvc.perform(post("/catalog/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(repository, never()).upsert(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void getCatalog_shouldReturnCatalog() throws Exception {
        String id = "w-001";
        Map<String, Object> catalog = Map.of(
                "id", id,
                "title", "Test Webtoon",
                "desc", "Test description",
                "tags", List.of("action")
        );

        when(service.findById(id)).thenReturn(Optional.of(catalog));

        mockMvc.perform(get("/catalog/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Test Webtoon"));

        verify(service).findById(id);
    }

    @Test
    void getCatalog_notFound_shouldReturnNotFound() throws Exception {
        String id = "non-existent";

        when(service.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/catalog/{id}", id))
                .andExpect(status().isNotFound());

        verify(service).findById(id);
    }
}
