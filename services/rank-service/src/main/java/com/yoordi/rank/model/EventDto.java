package com.yoordi.rank.model;

// TODO: Move to libs/common-domain after completing rank-service implementation
public record EventDto(
        String eventId,
        String userId,
        String contentId,
        long ts,
        Props props
) {
    
    public record Props(
            String action
    ) {}
}