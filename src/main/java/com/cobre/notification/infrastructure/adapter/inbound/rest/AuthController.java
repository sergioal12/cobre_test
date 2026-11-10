package com.cobre.notification.infrastructure.adapter.inbound.rest;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.infrastructure.security.ClientCredentialsService;
import com.cobre.notification.infrastructure.security.JwtTokenService;
import com.cobre.notification.infrastructure.security.JwtTokenService.GeneratedToken;
import com.cobre.notification.infrastructure.security.JwtTokenService.ValidatedToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
@Tag(name = "JWT Authentication API", description = "Client credentials -> signed HS256 JWT, and token validation")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final ClientCredentialsService clientCredentialsService;

    public AuthController(JwtTokenService jwtTokenService, ClientCredentialsService clientCredentialsService) {
        this.jwtTokenService = jwtTokenService;
        this.clientCredentialsService = clientCredentialsService;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Exchange client credentials for a signed HS256 JWT",
            description = "Authenticates the (client_id, client_secret) pair and issues a short-lived RFC 7519 JWT "
                    + "whose client_id claim is the only tenant identity accepted by /notification_events."
    )
    public ResponseEntity<TokenResponseDto> generateToken(@Valid @RequestBody TokenRequestDto requestBody) {
        String authenticatedClientId = clientCredentialsService.authenticate(
                requestBody.clientId(),
                requestBody.clientSecret()
        );

        GeneratedToken generated = jwtTokenService.generateToken(authenticatedClientId);
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
            description = "Verifies the HMAC-SHA256 signature and expiration of the Authorization: Bearer <token> header."
    )
    public ResponseEntity<TokenValidationResponseDto> validateToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        ValidatedToken validated = jwtTokenService.validateToken(extractBearerToken(authorizationHeader));
        return ResponseEntity.ok(new TokenValidationResponseDto(
                validated.valid(),
                validated.clientId(),
                validated.issuer(),
                validated.issuedAt(),
                validated.expiresAt()
        ));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()
                || !authorizationHeader.trim().regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedClientAccessException("Authorization: Bearer <token> header is required");
        }
        return authorizationHeader.trim().substring(7).trim();
    }

    public record TokenRequestDto(
            @NotBlank(message = "client_id is required")
            @Size(max = 64, message = "client_id must not exceed 64 characters")
            @JsonProperty("client_id") String clientId,
            @NotBlank(message = "client_secret is required")
            @Size(max = 256, message = "client_secret must not exceed 256 characters")
            @JsonProperty("client_secret") String clientSecret
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
