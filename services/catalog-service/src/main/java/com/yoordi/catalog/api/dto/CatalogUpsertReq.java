package com.yoordi.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CatalogUpsertReq(
    @NotBlank String id,
    @NotBlank String title,
    String desc,
    List<String> tags
) {}