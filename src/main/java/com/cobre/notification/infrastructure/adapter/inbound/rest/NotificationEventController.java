package com.cobre.notification.infrastructure.adapter.inbound.rest;

import com.cobre.notification.application.port.inbound.CreateNotificationEventUseCase;
import com.cobre.notification.application.port.inbound.CreateNotificationEventUseCase.CreateEventCommand;
import com.cobre.notification.application.port.inbound.QueryNotificationEventsUseCase;
import com.cobre.notification.application.port.inbound.QueryNotificationEventsUseCase.QueryCriteria;
import com.cobre.notification.application.port.inbound.ReplayNotificationUseCase;
import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.CreateNotificationEventRequestDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.DeadLetterNotificationResponseDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.NotificationEventDetailResponseDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.NotificationEventResponseDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.ReplayNotificationRequestDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.mapper.NotificationRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/notification_events")
@Tag(name = "Notification Events Self-Service API", description = "Endpoints for dispatching, querying, and replaying client notification events and inspecting the DLQ")
public class NotificationEventController {

    public static final String WEBHOOK_URL_HEADER = "X-Webhook-Url";

    private final QueryNotificationEventsUseCase queryUseCase;
    private final ReplayNotificationUseCase replayUseCase;
    private final CreateNotificationEventUseCase createUseCase;
    private final NotificationRestMapper mapper;

    public NotificationEventController(
            QueryNotificationEventsUseCase queryUseCase,
            ReplayNotificationUseCase replayUseCase,
            CreateNotificationEventUseCase createUseCase,
            NotificationRestMapper mapper
    ) {
        this.queryUseCase = queryUseCase;
        this.replayUseCase = replayUseCase;
        this.createUseCase = createUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(
            summary = "Create and dispatch a new notification event",
            description = "Creates a notification event of any type (e.g., credit_card_payment, debit_transfer, balance_update) and dispatches it to the client's webhook with up to 5 retry attempts before falling back to the DLQ."
    )
    public ResponseEntity<NotificationEventDetailResponseDto> createAndDispatchEvent(
            Authentication authentication,
            @RequestHeader(value = WEBHOOK_URL_HEADER, required = false) String headerWebhookUrl,
            @Valid @RequestBody CreateNotificationEventRequestDto requestBody
    ) {
        String clientId = resolveClientId(authentication, requestBody.clientId());

        NotificationEvent processed = createUseCase.createAndDispatch(new CreateEventCommand(
                clientId,
                requestBody.eventId(),
                requestBody.eventType(),
                requestBody.content(),
                firstNonBlank(requestBody.webhookUrl(), headerWebhookUrl)
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetailDto(processed));
    }

    @GetMapping
    @Operation(
            summary = "Query notification events per client",
            description = "Returns all notification events belonging to the authenticated client, with optional filters by creation/delivery date and delivery_status."
    )
    public ResponseEntity<List<NotificationEventResponseDto>> listNotificationEvents(
            Authentication authentication,
            @Parameter(description = "Optional; must match the authenticated client (JWT) if provided")
            @RequestParam(value = "client_id", required = false) String queryClientId,
            @Parameter(description = "Filter by delivery_status (completed, failed, pending, retrying)")
            @RequestParam(value = "delivery_status", required = false) String deliveryStatus,
            @Parameter(description = "Filter from date/timestamp (ISO-8601 Instant e.g. 2024-03-15T09:00:00Z or LocalDate 2024-03-15)")
            @RequestParam(value = "from_date", required = false) String fromDate,
            @Parameter(description = "Alias for from_date (creation_date_from)")
            @RequestParam(value = "creation_date_from", required = false) String creationDateFrom,
            @Parameter(description = "Filter to date/timestamp (ISO-8601 Instant e.g. 2024-03-15T18:00:00Z or LocalDate 2024-03-15)")
            @RequestParam(value = "to_date", required = false) String toDate,
            @Parameter(description = "Alias for to_date (creation_date_to)")
            @RequestParam(value = "creation_date_to", required = false) String creationDateTo,
            @Parameter(description = "Filter by single creation/delivery date (e.g. 2024-03-15 or exact ISO-8601 timestamp)")
            @RequestParam(value = "creation_date", required = false) String creationDate,
            @Parameter(description = "Alias for creation_date (delivery_date)")
            @RequestParam(value = "delivery_date", required = false) String deliveryDate
    ) {
        String effectiveClientId = resolveClientId(authentication, queryClientId);
        DeliveryStatus parsedStatus = deliveryStatus != null && !deliveryStatus.isBlank()
                ? DeliveryStatus.fromValue(deliveryStatus)
                : null;

        String rawSingleDate = firstNonBlank(creationDate, deliveryDate);
        String rawFrom = firstNonBlank(fromDate, creationDateFrom);
        String rawTo = firstNonBlank(toDate, creationDateTo);

        Instant parsedFrom = parseBoundary(rawFrom, rawSingleDate, false);
        Instant parsedTo = parseBoundary(rawTo, rawSingleDate, true);

        List<NotificationEventResponseDto> response = queryUseCase.findEventsByCriteria(
                        new QueryCriteria(effectiveClientId, parsedFrom, parsedTo, parsedStatus)
                )
                .stream()
                .map(mapper::toSummaryDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dlq")
    @Operation(
            summary = "Query Dead Letter Queue (DLQ) events per client",
            description = "Returns notifications stored in the 'notification_dlq' table after failing 5 retry attempts, with full context for manual reprocessing."
    )
    public ResponseEntity<List<DeadLetterNotificationResponseDto>> listClientDlqEvents(
            Authentication authentication,
            @RequestParam(value = "client_id", required = false) String queryClientId
    ) {
        String effectiveClientId = resolveClientId(authentication, queryClientId);
        List<DeadLetterNotificationResponseDto> response = queryUseCase.findDlqEventsByClient(effectiveClientId)
                .stream()
                .map(mapper::toDlqDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{notification_event_id}")
    @Operation(
            summary = "Get single notification event details",
            description = "Returns full details and delivery attempt history for a single notification event owned by the client."
    )
    public ResponseEntity<NotificationEventDetailResponseDto> getNotificationEventById(
            @PathVariable("notification_event_id") String notificationEventId,
            Authentication authentication,
            @RequestParam(value = "client_id", required = false) String queryClientId
    ) {
        String effectiveClientId = resolveClientId(authentication, queryClientId);
        NotificationEvent event = queryUseCase.getEventById(notificationEventId, effectiveClientId);
        return ResponseEntity.ok(mapper.toDetailDto(event));
    }

    @PostMapping("/{notification_event_id}/replay")
    @Operation(
            summary = "Replay a failed notification event",
            description = "Re-sends a notification event over HTTPS to the client's webhook when delivery has definitely failed (up to 5 retry attempts before updating DLQ)."
    )
    public ResponseEntity<NotificationEventDetailResponseDto> replayNotificationEvent(
            @PathVariable("notification_event_id") String notificationEventId,
            Authentication authentication,
            @RequestParam(value = "client_id", required = false) String queryClientId,
            @RequestHeader(value = WEBHOOK_URL_HEADER, required = false) String headerWebhookUrl,
            @RequestBody(required = false) ReplayNotificationRequestDto requestBody
    ) {
        String effectiveClientId = resolveClientId(authentication, queryClientId);
        String overrideWebhookUrl = firstNonBlank(
                requestBody != null ? requestBody.webhookUrl() : null,
                headerWebhookUrl
        );

        NotificationEvent replayed = replayUseCase.replayFailedNotification(
                notificationEventId,
                effectiveClientId,
                overrideWebhookUrl
        );
        return ResponseEntity.ok(mapper.toDetailDto(replayed));
    }

    /**
     * The tenant identity is ONLY the client_id claim of the validated JWT. A client_id supplied in the query
     * string or body is never trusted: it can only confirm the authenticated tenant, never switch to another one.
     */
    private String resolveClientId(Authentication authentication, String requestedClientId) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedClientAccessException(
                    "Authentication required: send 'Authorization: Bearer <jwt>' obtained from POST /auth/token"
            );
        }
        String authenticatedClientId = authentication.getName();

        if (requestedClientId != null && !requestedClientId.isBlank()
                && !authenticatedClientId.equals(requestedClientId.trim())) {
            throw new UnauthorizedClientAccessException(
                    "Cross-tenant access denied: 'client_id' does not match the authenticated client"
            );
        }
        return authenticatedClientId;
    }

    private Instant parseBoundary(String explicitDate, String fallbackSingleDate, boolean endOfDayIfLocalDate) {
        String candidate = firstNonBlank(explicitDate, fallbackSingleDate);
        return candidate != null ? parseFlexibleInstant(candidate.trim(), endOfDayIfLocalDate) : null;
    }

    private Instant parseFlexibleInstant(String value, boolean endOfDayIfLocalDate) {
        try {
            if (value.length() == 10) {
                LocalDate localDate = LocalDate.parse(value);
                return endOfDayIfLocalDate
                        ? localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                        : localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid date format '" + value + "'. Expected ISO-8601 timestamp (e.g. 2024-03-15T09:30:22Z) or date (2024-03-15)"
            );
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
