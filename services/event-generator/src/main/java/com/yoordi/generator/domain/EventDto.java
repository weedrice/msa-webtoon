package com.yoordi.generator.domain;

public record EventDto(
        String eventId,
        String userId,
        String contentId,
        long ts,
        Props props
) {
    public record Props(String action) {}
}