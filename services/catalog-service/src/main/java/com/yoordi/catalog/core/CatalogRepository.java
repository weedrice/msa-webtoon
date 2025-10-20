package com.yoordi.catalog.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String id, String title, String desc, List<String> tags) {
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                insert into catalog(id, title, "desc", tags, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (id) do update set 
                    title = excluded.title, 
                    "desc" = excluded."desc", 
                    tags = excluded.tags, 
                    updated_at = now()
                """);
            
            ps.setString(1, id);
            ps.setString(2, title);
            ps.setString(3, desc);
            
            Array tagsArray = null;
            if (tags != null && !tags.isEmpty()) {
                tagsArray = connection.createArrayOf("text", tags.toArray());
            }
            ps.setArray(4, tagsArray);
            
            return ps;
        });
    }
}