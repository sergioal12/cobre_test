package com.cobre.notification.infrastructure.adapter.inbound.rest;

import com.cobre.notification.domain.exception.EventNotFoundException;
import com.cobre.notification.domain.exception.InvalidReplayStateException;
import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleEventNotFound(
            EventNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidReplayStateException.class)
    public ResponseEntity<ApiErrorResponseDto> handleInvalidReplayState(
            InvalidReplayStateException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UnauthorizedClientAccessException.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnauthorizedClient(
            UnauthorizedClientAccessException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("Cross-tenant")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;
        return buildResponse(status, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponseDto> handleSecurityException(
            SecurityException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponseDto> buildResponse(HttpStatus status, String message, String path) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
        return ResponseEntity.status(status).body(body);
    }
}
