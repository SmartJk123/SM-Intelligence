package com.smi.notifications_service.controller;

import com.smi.notifications_service.dto.CreateNotificationRequest;
import com.smi.notifications_service.dto.NotificationResponse;
import com.smi.notifications_service.dto.UnreadCountResponse;
import com.smi.notifications_service.security.NotificationIdentity;
import com.smi.notifications_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationIdentity identity;

    public NotificationController(NotificationService notificationService, NotificationIdentity identity) {
        this.notificationService = notificationService;
        this.identity = identity;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> listNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(notificationService.getUserNotifications(effectiveUserId, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.getUnreadCount(effectiveUserId)));
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @Valid @RequestBody CreateNotificationRequest request) {
        UUID targetUserId = request.userId();
        if (!identity.isInternalService(internalToken) && targetUserId == null) {
            targetUserId = identity.resolveUserId(authorization, xUserId, null);
            request = new CreateNotificationRequest(
                    targetUserId,
                    request.eventType(),
                    request.severity(),
                    request.title(),
                    request.message(),
                    request.referenceId(),
                    request.referenceType(),
                    request.metadata(),
                    request.deliveryChannels()
            );
        }
        NotificationResponse created = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(notificationService.markAsRead(id, effectiveUserId));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        int count = notificationService.markAllAsRead(effectiveUserId);
        return ResponseEntity.ok(Map.of("markedReadCount", count, "success", true));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<NotificationResponse> resolveNotification(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        return ResponseEntity.ok(notificationService.resolveNotification(id, effectiveUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(required = false) UUID userId) {
        UUID effectiveUserId = identity.resolveUserId(authorization, xUserId, userId);
        notificationService.deleteNotification(id, effectiveUserId);
        return ResponseEntity.noContent().build();
    }
}
