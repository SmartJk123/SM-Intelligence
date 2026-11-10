package com.smi.notifications_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.notifications_service.domain.Notification;
import com.smi.notifications_service.domain.NotificationOutbox;
import com.smi.notifications_service.domain.NotificationPreference;
import com.smi.notifications_service.dto.CreateNotificationRequest;
import com.smi.notifications_service.dto.NotificationResponse;
import com.smi.notifications_service.exception.NotificationNotFoundException;
import com.smi.notifications_service.repository.NotificationOutboxRepository;
import com.smi.notifications_service.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final PreferenceService preferenceService;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationOutboxRepository outboxRepository,
                               PreferenceService preferenceService,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.preferenceService = preferenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        NotificationPreference pref = preferenceService.getEffectivePreference(request.userId(), request.eventType());

        String severity = (request.severity() == null || request.severity().isBlank()) ? "INFO" : request.severity().toUpperCase();
        Notification notification = new Notification(
                request.userId(),
                request.eventType(),
                severity,
                request.title(),
                request.message()
        );

        if (request.referenceId() != null) notification.setReferenceId(request.referenceId());
        if (request.referenceType() != null) notification.setReferenceType(request.referenceType());
        if (request.metadata() != null) notification.setMetadata(request.metadata());

        // If in-app notification is disabled by user preference, record it as already read
        if (!pref.isInAppEnabled()) {
            notification.setReadAt(OffsetDateTime.now());
        }

        Notification saved = notificationRepository.save(notification);

        // Transactional Outbox for external channels
        if (request.deliveryChannels() != null && !request.deliveryChannels().isEmpty()) {
            for (String channel : request.deliveryChannels()) {
                String ch = channel.toUpperCase().trim();
                boolean allowed = switch (ch) {
                    case "EMAIL" -> pref.isEmailEnabled();
                    case "PUSH", "PUSH_FCM" -> pref.isPushEnabled();
                    default -> true;
                };

                if (allowed) {
                    try {
                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("notificationId", saved.getId());
                        payloadMap.put("userId", saved.getUserId());
                        payloadMap.put("title", saved.getTitle());
                        payloadMap.put("message", saved.getMessage());
                        payloadMap.put("eventType", saved.getEventType());
                        payloadMap.put("severity", saved.getSeverity());

                        String payloadJson = objectMapper.writeValueAsString(payloadMap);
                        NotificationOutbox outbox = new NotificationOutbox(saved.getId(), ch, payloadJson);
                        outboxRepository.save(outbox);
                    } catch (Exception ex) {
                        log.error("Failed to serialize outbox payload for notification {}: {}", saved.getId(), ex.getMessage());
                    }
                }
            }
        }

        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, Boolean unreadOnly, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<Notification> result;

        if (Boolean.TRUE.equals(unreadOnly)) {
            result = notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageRequest);
        } else if (Boolean.FALSE.equals(unreadOnly)) {
            result = notificationRepository.findByUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(userId, pageRequest);
        } else {
            result = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        }

        return result.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markAsRead();
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadForUser(userId, OffsetDateTime.now());
    }

    @Transactional
    public NotificationResponse resolveNotification(UUID id, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markAsResolved();
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void deleteNotification(UUID id, UUID userId) {
        notificationRepository.deleteByIdAndUserId(id, userId);
    }
}
