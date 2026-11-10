package com.cobre.notification.infrastructure.adapter.inbound.rest;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.infrastructure.security.JwtTokenService;
import com.cobre.notification.infrastructure.security.JwtTokenService.GeneratedToken;
import com.cobre.notification.infrastructure.security.JwtTokenService.ValidatedToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
@Tag(name = "JWT Authentication API", description = "Endpoints to generate and validate HS256 JWT tokens per client_id")
public class AuthController {

    private final JwtTokenService jwtTokenService;

    public AuthController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Generate a signed HS256 JWT token for a client",
            description = "Issues a signed RFC 7519 JWT token containing the client_id claim. Provide client_id in the JSON body, query parameter, or X-Client-Id header."
    )
    public ResponseEntity<TokenResponseDto> generateToken(
            @RequestHeader(value = NotificationEventController.CLIENT_ID_HEADER, required = false) String headerClientId,
            @RequestParam(value = "client_id", required = false) String queryClientId,
            @RequestBody(required = false) TokenRequestDto requestBody
    ) {
        String effectiveClientId = firstNonBlank(
                requestBody != null ? requestBody.clientId() : null,
                queryClientId,
                headerClientId
        );

        if (effectiveClientId == null) {
            throw new IllegalArgumentException(
                    "client_id is required in request body, query param, or X-Client-Id header to generate a JWT token"
            );
        }

        GeneratedToken generated = jwtTokenService.generateToken(effectiveClientId);
        return ResponseEntity.ok(new TokenResponseDto(
                generated.accessToken(),
                generated.tokenType(),
                generated.expiresIn(),
                generated.clientId(),
                generated.issuedAt(),
                generated.expiresAt()
        ));
    }

    @GetMapping("/validate")
    @Operation(
            summary = "Validate a Bearer JWT token and inspect its claims",
            description = "Verifies the HMAC-SHA256 signature and expiration of the Authorization: Bearer <token> header and returns the decoded claims."
    )
    public ResponseEntity<TokenValidationResponseDto> validateToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(value = "token", required = false) String tokenParam
    ) {
        String rawToken = extractBearerToken(authorizationHeader, tokenParam);
        ValidatedToken validated = jwtTokenService.validateToken(rawToken);
        return ResponseEntity.ok(new TokenValidationResponseDto(
                validated.valid(),
                validated.clientId(),
                validated.issuer(),
                validated.issuedAt(),
                validated.expiresAt()
        ));
    }

    private String extractBearerToken(String authorizationHeader, String tokenParam) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            String trimmed = authorizationHeader.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam.trim();
        }
        throw new UnauthorizedClientAccessException("Authorization: Bearer <token> header is required");
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    public record TokenRequestDto(
            @JsonProperty("client_id") String clientId
    ) {
    }

    public record TokenResponseDto(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("issued_at") Instant issuedAt,
            @JsonProperty("expires_at") Instant expiresAt
    ) {
    }

    public record TokenValidationResponseDto(
            @JsonProperty("valid") boolean valid,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("issuer") String issuer,
            @JsonProperty("issued_at") Instant issuedAt,
            @JsonProperty("expires_at") Instant expiresAt
    ) {
    }
}

