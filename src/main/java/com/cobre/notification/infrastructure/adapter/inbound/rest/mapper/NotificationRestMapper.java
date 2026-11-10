package com.cobre.notification.infrastructure.adapter.inbound.rest.mapper;

import com.cobre.notification.domain.model.DeadLetterNotification;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.DeadLetterNotificationResponseDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.NotificationEventDetailResponseDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.NotificationEventDetailResponseDto.DeliveryAttemptDto;
import com.cobre.notification.infrastructure.adapter.inbound.rest.dto.NotificationEventResponseDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationRestMapper {

    public NotificationEventResponseDto toSummaryDto(NotificationEvent domain) {
        return new NotificationEventResponseDto(
                domain.getEventId(),
                domain.getEventType(),
                domain.getContent(),
                domain.getDeliveryDate(),
                domain.getDeliveryStatus().getValue(),
                domain.getClientId(),
                domain.getAttemptCount(),
                domain.getWebhookUrl()
        );
    }

    public NotificationEventDetailResponseDto toDetailDto(NotificationEvent domain) {
        List<DeliveryAttemptDto> attemptDtos = domain.getDeliveryAttempts().stream()
                .map(attempt -> new DeliveryAttemptDto(
                        attempt.attemptNumber(),
                        attempt.httpStatusCode(),
                        attempt.success(),
                        attempt.latencyMs(),
                        attempt.errorMessage(),
                        attempt.attemptedAt()
                ))
                .toList();

        return new NotificationEventDetailResponseDto(
                domain.getEventId(),
                domain.getEventType(),
                domain.getContent(),
                domain.getDeliveryDate(),
                domain.getDeliveryStatus().getValue(),
                domain.getClientId(),
                domain.getWebhookUrl(),
                domain.getAttemptCount(),
                domain.getLastErrorMessage(),
                attemptDtos
        );
    }

    public DeadLetterNotificationResponseDto toDlqDto(DeadLetterNotification dlq) {
        return new DeadLetterNotificationResponseDto(
                dlq.id(),
                dlq.eventId(),
                dlq.clientId(),
                dlq.eventType(),
                dlq.payloadContent(),
                dlq.targetWebhookUrl(),
                dlq.retryAttemptsExhausted(),
                dlq.lastHttpStatusCode(),
                dlq.failureReason(),
                dlq.status().getValue(),
                dlq.createdAt(),
                dlq.lastReprocessedAt()
        );
    }
}
