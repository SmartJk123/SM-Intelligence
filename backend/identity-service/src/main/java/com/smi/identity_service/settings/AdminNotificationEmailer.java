package com.smi.identity_service.settings;

import com.smi.identity_service.activity.ActivityLog;
import com.smi.identity_service.service.PasswordResetMailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Applies the saved Settings that act in the background:
 * emails the administrators' notifications that Settings, Notifications has
 * switched on, and deletes audit entries older than Settings, System's retention.
 * Service outage emails are sent by ServiceHealthMonitor, which reads the same settings.
 */
@Component
public class AdminNotificationEmailer {

    private final PlatformSettings settings;
    private final PasswordResetMailer mailer;
    private final List<String> adminEmails;
    private final com.smi.identity_service.activity.AuditEventRepository auditEvents;
    private final TransactionTemplate transaction;

    public AdminNotificationEmailer(PlatformSettings settings, PasswordResetMailer mailer,
                                    @Value("${app.admin.emails:}") String adminEmails,
                                    com.smi.identity_service.activity.AuditEventRepository auditEvents,
                                    org.springframework.transaction.PlatformTransactionManager transactions) {
        this.settings = settings;
        this.mailer = mailer;
        this.adminEmails = Arrays.stream(adminEmails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.auditEvents = auditEvents;
        this.transaction = new TransactionTemplate(transactions);
    }

    @EventListener
    public void onNotification(ActivityLog.AdminNotified notification) {
        String setting = switch (notification.category()) {
            case "Security" -> "emailSecurity";
            case "Customer" -> switch (notification.title()) {
                case "New sign up", "Customer added" -> "emailNewSignUps";
                case "New customer signed in" -> "emailFirstSignIns";
                default -> "emailAccountChanges";
            };
            // System alerts are emailed by ServiceHealthMonitor with fuller detail.
            default -> null;
        };
        if (setting == null || !settings.flag("notifications", setting)) {
            return;
        }
        Set<String> chosen = settings.list("notifications", "recipients");
        List<String> recipients = chosen.isEmpty() ? adminEmails : List.copyOf(chosen);
        String subject = "[SmartMoney] " + notification.title();
        String body = notification.message() + "\n\nSee Notifications in the admin portal for details.\n"
                + "Change which notifications are emailed under Settings, Notifications.\n";
        // Off the request thread, so a slow mail server never delays a sign in.
        Thread.ofVirtual().start(() -> recipients.forEach(to -> mailer.send(to, subject, body, "ADMIN_NOTIFICATION")));
    }

    /** Daily at 02:30: deletes audit entries older than the retention period (0 keeps everything). */
    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Nairobi")
    public void applyAuditRetention() {
        int days = settings.number("system", "auditRetentionDays");
        if (days > 0) {
            transaction.executeWithoutResult(status ->
                    auditEvents.deleteByOccurredAtBefore(OffsetDateTime.now().minusDays(days)));
        }
    }
}
