package com.smi.notifications_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateNotificationRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "eventType is required")
        String eventType,

        String severity, // INFO, WARNING, CRITICAL, SUCCESS (defaults to INFO)

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "message is required")
        String message,

        UUID referenceId,

        String referenceType,

        String metadata,

        List<String> deliveryChannels // e.g. ["EMAIL", "PUSH_FCM"]
) {
}
