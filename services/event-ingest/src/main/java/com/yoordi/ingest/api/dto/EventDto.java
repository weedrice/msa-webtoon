package com.yoordi.ingest.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventDto(
        @NotBlank(message = "eventId must not be blank")
        String eventId,
        
        String userId,
        
        @NotBlank(message = "contentId must not be blank")
        String contentId,
        
        @Positive(message = "ts must be positive")
        long ts,
        
        @Valid
        @NotNull(message = "props must not be null")
        Props props
) {
    
    public record Props(
            @NotBlank(message = "action must not be blank")
            String action
    ) {
        public Props {
            if (action != null && !action.equals("view") && !action.equals("like")) {
                throw new IllegalArgumentException("action must be 'view' or 'like'");
            }
        }
    }
}
