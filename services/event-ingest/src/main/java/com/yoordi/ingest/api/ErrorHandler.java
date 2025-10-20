package com.yoordi.ingest.api;

import com.yoordi.ingest.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {

        String traceId = getOrGenerateTraceId();
        log.warn("Validation error [traceId: {}]: {}", traceId, ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()
                ))
                .toList();

        ErrorResponse errorResponse = ErrorResponse.withValidationErrors(
                HttpStatus.BAD_REQUEST.value(),
                "Event validation failed",
                getPath(request),
                traceId,
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        String traceId = getOrGenerateTraceId();
        log.warn("Invalid argument [traceId: {}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ARGUMENT",
                ex.getMessage(),
                getPath(request),
                traceId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(
            KafkaException ex, WebRequest request) {

        String traceId = getOrGenerateTraceId();
        log.error("Kafka publishing error [traceId: {}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "EVENT_PUBLISHING_ERROR",
                "Failed to publish event. Please try again later.",
                getPath(request),
                traceId
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String traceId = getOrGenerateTraceId();
        log.error("Unexpected error [traceId: {}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred while processing the event.",
                getPath(request),
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private String getOrGenerateTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }
        return traceId;
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}