package com.cobre.notification.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";
    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI cobreNotificationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cobre Event Notification Self-Service API")
                        .version("1.0.0")
                        .description("Hexagonal Architecture microservice for webhook event notification delivery and client self-service queries/replays.")
                        .contact(new Contact().name("Cobre Platform Engineering")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH_SCHEME)
                        .addList(CLIENT_ID_HEADER))
                .schemaRequirement(BEARER_AUTH_SCHEME, new SecurityScheme()
                        .name(BEARER_AUTH_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Signed HS256 JWT token issued by POST /auth/token"))
                .schemaRequirement(CLIENT_ID_HEADER, new SecurityScheme()
                        .name(CLIENT_ID_HEADER)
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .description("Authenticated Client Identifier (e.g., CLIENT001, CLIENT002, CLIENT003)"));
    }
}

