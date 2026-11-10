package com.smi.notifications_service.dto;

import com.smi.notifications_service.domain.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String eventType,
        String severity,
        String title,
        String message,
        UUID referenceId,
        String referenceType,
        OffsetDateTime readAt,
        OffsetDateTime resolvedAt,
        String metadata,
        OffsetDateTime createdAt,
        boolean isRead,
        boolean isResolved
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getEventType(),
                n.getSeverity(),
                n.getTitle(),
                n.getMessage(),
                n.getReferenceId(),
                n.getReferenceType(),
                n.getReadAt(),
                n.getResolvedAt(),
                n.getMetadata(),
                n.getCreatedAt(),
                n.isRead(),
                n.isResolved()
        );
    }
}
