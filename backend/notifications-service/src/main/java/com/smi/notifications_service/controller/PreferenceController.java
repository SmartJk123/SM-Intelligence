package com.smi.notifications_service.controller;

import com.smi.notifications_service.dto.NotificationPreferenceResponse;
import com.smi.notifications_service.dto.UpdatePreferenceRequest;
import com.smi.notifications_service.security.NotificationIdentity;
import com.smi.notifications_service.service.PreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final NotificationIdentity identity;

    public PreferenceController(PreferenceService preferenceService, NotificationIdentity identity) {
        this.preferenceService = preferenceService;
        this.identity = identity;
    }

    @GetMapping
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(preferenceService.getPreferences(effectiveUserId));
    }

    @PutMapping
    public ResponseEntity<NotificationPreferenceResponse> updateGeneralPreference(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId,
            @RequestBody UpdatePreferenceRequest request) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(preferenceService.updatePreference(effectiveUserId, PreferenceService.DEFAULT_EVENT_TYPE, request));
    }

    @PutMapping("/{eventType}")
    public ResponseEntity<NotificationPreferenceResponse> updateEventPreference(
            @PathVariable String eventType,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId,
            @RequestBody UpdatePreferenceRequest request) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(preferenceService.updatePreference(effectiveUserId, eventType, request));
    }
}
