package com.yoordi.ingest.api;

import com.yoordi.ingest.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorHandlerTest {

    private final ErrorHandler handler = new ErrorHandler();

    @Test
    void handleValidationExceptions_shouldReturnBadRequest() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        WebRequest request = mock(WebRequest.class);

        FieldError fieldError = new FieldError("eventDto", "eventId", "", false, null, null, "must not be null");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(request.getDescription(false)).thenReturn("uri=/ingest/events");

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().error());
        assertEquals("Event validation failed", response.getBody().message());
        assertEquals(1, response.getBody().errors().size());
    }

    @Test
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid event data");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/ingest/events");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().error());
        assertEquals("Invalid event data", response.getBody().message());
    }

    @Test
    void handleKafkaException_shouldReturnServiceUnavailable() {
        KafkaException ex = new KafkaException("Kafka broker unavailable");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/ingest/events");

        ResponseEntity<ErrorResponse> response = handler.handleKafkaException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("EVENT_PUBLISHING_ERROR", response.getBody().error());
        assertTrue(response.getBody().message().contains("Failed to publish event"));
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        Exception ex = new Exception("Unexpected error");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/ingest/events");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().error());
        assertTrue(response.getBody().message().contains("unexpected error"));
    }
}
