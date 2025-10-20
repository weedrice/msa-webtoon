package com.yoordi.ingest.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public record ErrorResponse(
        @Schema(description = "Error timestamp", example = "2024-01-01T12:00:00Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Error type", example = "VALIDATION_ERROR")
        String error,

        @Schema(description = "Error message", example = "Invalid request data")
        String message,

        @Schema(description = "Request path", example = "/ingest/events")
        String path,

        @Schema(description = "Trace ID for debugging", example = "abc123def456")
        String traceId,

        @Schema(description = "Detailed validation errors")
        List<FieldError> errors
) {
    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, traceId, null);
    }

    public static ErrorResponse withValidationErrors(int status, String message, String path, String traceId, List<FieldError> errors) {
        return new ErrorResponse(Instant.now(), status, "VALIDATION_ERROR", message, path, traceId, errors);
    }

    @Schema(description = "Field-level validation error")
    public record FieldError(
            @Schema(description = "Field name", example = "eventId")
            String field,

            @Schema(description = "Rejected value", example = "")
            Object rejectedValue,

            @Schema(description = "Error message", example = "EventId cannot be empty")
            String message
    ) {}
}