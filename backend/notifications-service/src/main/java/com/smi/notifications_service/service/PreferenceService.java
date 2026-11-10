package com.smi.notifications_service.service;

import com.smi.notifications_service.domain.NotificationPreference;
import com.smi.notifications_service.dto.NotificationPreferenceResponse;
import com.smi.notifications_service.dto.UpdatePreferenceRequest;
import com.smi.notifications_service.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PreferenceService {

    public static final String DEFAULT_EVENT_TYPE = "ALL";

    private final NotificationPreferenceRepository repository;

    public PreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(UUID userId) {
        List<NotificationPreference> existing = repository.findByUserId(userId);
        if (existing.isEmpty()) {
            return List.of(NotificationPreferenceResponse.from(
                    new NotificationPreference(userId, DEFAULT_EVENT_TYPE, true, true, false)));
        }
        return existing.stream().map(NotificationPreferenceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public NotificationPreference getEffectivePreference(UUID userId, String eventType) {
        // First check specific event type preference
        Optional<NotificationPreference> specific = repository.findByUserIdAndEventType(userId, eventType);
        if (specific.isPresent()) {
            return specific.get();
        }
        // Fall back to 'ALL' general preference if defined
        return repository.findByUserIdAndEventType(userId, DEFAULT_EVENT_TYPE)
                .orElseGet(() -> new NotificationPreference(userId, eventType, true, true, false));
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId, String eventType, UpdatePreferenceRequest request) {
        String type = (eventType == null || eventType.isBlank()) ? DEFAULT_EVENT_TYPE : eventType.toUpperCase();
        NotificationPreference pref = repository.findByUserIdAndEventType(userId, type)
                .orElseGet(() -> new NotificationPreference(userId, type, true, true, false));

        boolean inApp = request.inAppEnabled() != null ? request.inAppEnabled() : pref.isInAppEnabled();
        boolean email = request.emailEnabled() != null ? request.emailEnabled() : pref.isEmailEnabled();
        boolean push = request.pushEnabled() != null ? request.pushEnabled() : pref.isPushEnabled();

        pref.update(inApp, email, push);
        NotificationPreference saved = repository.save(pref);
        return NotificationPreferenceResponse.from(saved);
    }
}
