package com.smi.identity_service.activity;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes the audit trail, the administrators' notifications and the email outbox.
 *
 * Each write runs in its own transaction and never throws: a failed sign in must
 * still be recorded after the caller's transaction rolls back, and a problem with
 * these tables must never stop anyone signing in or receiving an email.
 */
@Service
public class ActivityLog {

    private static final Logger log = LoggerFactory.getLogger(ActivityLog.class);
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("(?i)(token=)[^\\s&]+");

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    /** Who did something. STAFF are platform administrators, CUSTOMER everyone else. */
    public record Actor(UUID id, String email, String role) {
        public static Actor of(User user) {
            return new Actor(user.getId(), user.getEmailAddress(), user.isPlatformAdmin() ? "STAFF" : "CUSTOMER");
        }

        /** Someone not (yet) identified, such as a failed sign in with this email address. */
        public static Actor unknown(String email) {
            return new Actor(null, email == null ? null : email.trim().toLowerCase(), "CUSTOMER");
        }

        public static Actor system() {
            return new Actor(null, null, "SYSTEM");
        }
    }

    private final AuditEventRepository auditEvents;
    private final AdminNotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final TransactionTemplate separateTransaction;
    private final UserRepository users;

    /** Published after each administrator notification, so AdminNotificationEmailer can email it. */
    public record AdminNotified(String category, String severity, String title, String message) {
    }

    private final org.springframework.context.ApplicationEventPublisher events;

    public ActivityLog(AuditEventRepository auditEvents, AdminNotificationRepository notifications,
                       EmailOutboxRepository outbox, PlatformTransactionManager transactions, UserRepository users,
                       org.springframework.context.ApplicationEventPublisher events) {
        this.users = users;
        this.events = events;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.outbox = outbox;
        this.separateTransaction = new TransactionTemplate(transactions);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** The signed-in caller of this request (an administrator on /api/v1/admin), or SYSTEM. */
    public Actor currentActor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UUID id) {
                return users.findById(id).map(Actor::of).orElse(Actor.system());
            }
        } catch (RuntimeException error) {
            log.debug("Could not identify the caller: {}", error.getMessage());
        }
        return Actor.system();
    }

    public void audit(Actor actor, String action, String targetType, String targetId, String targetLabel,
                      String detail, String outcome) {
        String ip = clientAddress();
        write("audit event " + action, () -> auditEvents.save(new AuditEvent(actor.id(), actor.email(), actor.role(),
                action, targetType, targetId, targetLabel, detail, ip, outcome)));
    }

    /** How many times action happened for this email address since the given moment. */
    public long countRecent(String action, String email, java.time.OffsetDateTime since) {
        try {
            return auditEvents.countByActionAndActorEmailAndOccurredAtAfter(action, email, since);
        } catch (RuntimeException error) {
            log.warn("Could not count recent {} events: {}", action, error.getMessage());
            return 0;
        }
    }

    /** An audit event about a user account (sign in, suspension, profile change...). */
    public void audit(Actor actor, String action, User target, String detail) {
        audit(actor, action, "USER", target.getId().toString(), target.getEmailAddress(), detail, SUCCESS);
    }

    /** category: Customer, Security, System... severity: INFO, WARNING or CRITICAL. */
    public void notifyAdmins(String category, String severity, String title, String message) {
        write("notification " + title,
                () -> notifications.save(new AdminNotification(category, severity, title, message)));
        try {
            events.publishEvent(new AdminNotified(category, severity, title, message));
        } catch (RuntimeException error) {
            log.warn("Could not pass on the notification {}: {}", title, error.getMessage());
        }
    }

    /**
     * Keeps a copy of an email for the Outbox. Every value in secrets (a temporary
     * password, a one-time link) and any token= in a link is stored as [hidden].
     */
    public void recordEmail(String to, String subject, String body, String templateKey, String status,
                            String error, String... secrets) {
        String stored = body == null ? "" : body;
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                stored = stored.replace(secret, "[hidden]");
            }
        }
        stored = TOKEN_IN_LINK.matcher(stored).replaceAll("$1[hidden]");
        String body2 = stored;
        write("outbox entry " + subject,
                () -> outbox.save(new EmailOutboxEntry(to, subject, body2, templateKey, status, error)));
    }

    private void write(String what, Runnable work) {
        try {
            separateTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException error) {
            log.warn("Could not store the {}: {}", what, error.getMessage());
        }
    }

    /** The caller's address, preferring the first X-Forwarded-For entry set by the gateway or web adapter. */
    private static String clientAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
