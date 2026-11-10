package com.smi.identity_service.activity;

import com.smi.identity_service.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The admin portal's Audit Logs and Notifications pages: the audit trail, the
 * administrators' notifications, the email outbox and the email templates.
 * Requires a PLATFORM_ADMIN token, like everything under /api/v1/admin.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminActivityController {

    private static final int MAX_ROWS = 1000;

    public record AuditEventView(UUID id, OffsetDateTime occurredAt, UUID actorId, String actorEmail,
                                 String actorRole, String action, String targetType, String targetId,
                                 String targetLabel, String detail, String ipAddress, String outcome) {
        static AuditEventView of(AuditEvent event) {
            return new AuditEventView(event.getId(), event.getOccurredAt(), event.getActorId(), event.getActorEmail(),
                    event.getActorRole(), event.getAction(), event.getTargetType(), event.getTargetId(),
                    event.getTargetLabel(), event.getDetail(), event.getIpAddress(), event.getOutcome());
        }
    }

    public record NotificationView(UUID id, OffsetDateTime createdAt, String category, String severity, String title,
                                   String message, boolean read) {
        static NotificationView of(AdminNotification notification) {
            return new NotificationView(notification.getId(), notification.getCreatedAt(), notification.getCategory(),
                    notification.getSeverity(), notification.getTitle(), notification.getMessage(),
                    notification.getReadAt() != null);
        }
    }

    public record OutboxView(UUID id, OffsetDateTime createdAt, String recipient, String subject, String body,
                             String templateKey, String status, String error) {
        static OutboxView of(EmailOutboxEntry entry) {
            return new OutboxView(entry.getId(), entry.getCreatedAt(), entry.getRecipient(), entry.getSubject(),
                    entry.getBody(), entry.getTemplateKey(), entry.getStatus(), entry.getError());
        }
    }

    public record TemplateUpdate(String subject, String body) {
    }

    private final AuditEventRepository auditEvents;
    private final AdminNotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final EmailTemplates templates;
    private final UserRepository users;
    private final ActivityLog activity;

    public AdminActivityController(AuditEventRepository auditEvents, AdminNotificationRepository notifications,
                                   EmailOutboxRepository outbox, EmailTemplates templates, UserRepository users,
                                   ActivityLog activity) {
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.outbox = outbox;
        this.templates = templates;
        this.users = users;
        this.activity = activity;
    }

    @GetMapping("/audit-events")
    @Transactional(readOnly = true)
    public List<AuditEventView> auditEvents(@RequestParam(defaultValue = "500") int limit) {
        return auditEvents.findAllByOrderByOccurredAtDesc(page(limit)).stream().map(AuditEventView::of).toList();
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<NotificationView> notifications(@RequestParam(defaultValue = "200") int limit) {
        return notifications.findAllByOrderByCreatedAtDesc(page(limit)).stream().map(NotificationView::of).toList();
    }

    @PostMapping("/notifications/{id}/read")
    @Transactional
    public ResponseEntity<NotificationView> markRead(@PathVariable UUID id) {
        return notifications.findById(id)
                .map(notification -> {
                    notification.markRead();
                    return ResponseEntity.ok(NotificationView.of(notifications.save(notification)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public Map<String, Integer> markAllRead() {
        List<AdminNotification> unread = notifications.findByReadAtIsNull();
        unread.forEach(AdminNotification::markRead);
        notifications.saveAll(unread);
        return Map.of("marked", unread.size());
    }

    @GetMapping("/email-outbox")
    @Transactional(readOnly = true)
    public List<OutboxView> outbox(@RequestParam(defaultValue = "200") int limit) {
        return outbox.findAllByOrderByCreatedAtDesc(page(limit)).stream().map(OutboxView::of).toList();
    }

    @GetMapping("/email-templates")
    public List<EmailTemplates.View> templates() {
        return templates.list();
    }

    @PutMapping("/email-templates/{key}")
    public EmailTemplates.View updateTemplate(@PathVariable String key, @RequestBody TemplateUpdate update,
                                              @AuthenticationPrincipal UUID adminId) {
        String editor = editorEmail(adminId);
        EmailTemplates.View view = templates.update(key, update.subject(), update.body(), editor);
        activity.audit(actor(adminId), "EMAIL_TEMPLATE_UPDATED", "EMAIL_TEMPLATE", key, view.name(), null,
                ActivityLog.SUCCESS);
        return view;
    }

    @PostMapping("/email-templates/{key}/reset")
    public EmailTemplates.View resetTemplate(@PathVariable String key, @AuthenticationPrincipal UUID adminId) {
        EmailTemplates.View view = templates.reset(key);
        activity.audit(actor(adminId), "EMAIL_TEMPLATE_RESET", "EMAIL_TEMPLATE", key, view.name(),
                "Restored the built-in wording", ActivityLog.SUCCESS);
        return view;
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, MAX_ROWS)));
    }

    private String editorEmail(UUID adminId) {
        return adminId == null ? null : users.findById(adminId).map(user -> user.getEmailAddress()).orElse(null);
    }

    private ActivityLog.Actor actor(UUID adminId) {
        return adminId == null
                ? ActivityLog.Actor.system()
                : users.findById(adminId).map(ActivityLog.Actor::of).orElse(ActivityLog.Actor.system());
    }
}
