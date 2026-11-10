package com.cobre.notification.infrastructure.security;

import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.ApiErrorResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Renders authentication failures with the same JSON error contract as {@code GlobalExceptionHandler}.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String DEFAULT_MESSAGE =
            "Authentication required: send 'Authorization: Bearer <jwt>' obtained from POST /auth/token";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String message = authException instanceof BadCredentialsException && authException.getMessage() != null
                ? authException.getMessage()
                : DEFAULT_MESSAGE;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer");
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponseDto(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }
}

