package com.smi.notifications_service.dto;

import com.smi.notifications_service.domain.NotificationPreference;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        UUID userId,
        String eventType,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean pushEnabled,
        OffsetDateTime updatedAt
) {
    public static NotificationPreferenceResponse from(NotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.getId(),
                p.getUserId(),
                p.getEventType(),
                p.isInAppEnabled(),
                p.isEmailEnabled(),
                p.isPushEnabled(),
                p.getUpdatedAt()
        );
    }
}
