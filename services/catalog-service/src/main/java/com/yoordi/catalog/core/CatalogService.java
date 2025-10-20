package com.yoordi.catalog.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;

    public CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Map<String, Object>> findById(String id) {
        return jdbc.query(
            "select id, title, \"desc\", tags, updated_at from catalog where id = ?",
            rs -> {
                if (rs.next()) {
                    Array tagsArray = rs.getArray("tags");
                    List<String> tags = null;
                    
                    if (tagsArray != null) {
                        try {
                            Object[] tagsObjects = (Object[]) tagsArray.getArray();
                            tags = List.of((String[]) java.util.Arrays.copyOf(tagsObjects, tagsObjects.length, String[].class));
                        } catch (SQLException e) {
                            // Handle exception, tags will remain null
                        }
                    }

                    return Optional.of(Map.of(
                        "id", rs.getString("id"),
                        "title", rs.getString("title"),
                        "desc", rs.getString("desc") != null ? rs.getString("desc") : "",
                        "tags", tags != null ? tags : List.of(),
                        "updatedAt", rs.getTimestamp("updated_at").toInstant()
                    ));
                }
                return Optional.<Map<String, Object>>empty();
            },
            id
        );
    }
}