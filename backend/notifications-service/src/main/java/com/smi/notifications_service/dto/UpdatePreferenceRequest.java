package com.smi.notifications_service.dto;

public record UpdatePreferenceRequest(
        Boolean inAppEnabled,
        Boolean emailEnabled,
        Boolean pushEnabled
) {
}
