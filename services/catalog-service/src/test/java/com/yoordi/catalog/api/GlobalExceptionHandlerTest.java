package com.yoordi.catalog.api;

import com.yoordi.catalog.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
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

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationExceptions_shouldReturnBadRequest() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        WebRequest request = mock(WebRequest.class);

        FieldError fieldError = new FieldError("request", "title", "", false, null, null, "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(request.getDescription(false)).thenReturn("uri=/catalog/upsert");

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().error());
        assertEquals(1, response.getBody().errors().size());
    }

    @Test
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/catalog/test");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().error());
        assertEquals("Invalid argument", response.getBody().message());
    }

    @Test
    void handleDataAccessException_shouldReturnInternalServerError() {
        DataAccessException ex = mock(DataAccessException.class);
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/catalog/test");
        when(ex.getMessage()).thenReturn("DB error");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DATABASE_ERROR", response.getBody().error());
    }

    @Test
    void handleKafkaException_shouldReturnServiceUnavailable() {
        KafkaException ex = new KafkaException("Kafka error");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/catalog/test");

        ResponseEntity<ErrorResponse> response = handler.handleKafkaException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MESSAGING_ERROR", response.getBody().error());
    }

    @Test
    void handleRuntimeException_shouldReturnInternalServerError() {
        RuntimeException ex = new RuntimeException("Runtime error");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/catalog/test");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().error());
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        Exception ex = new Exception("Generic error");
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/catalog/test");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNKNOWN_ERROR", response.getBody().error());
    }
}
