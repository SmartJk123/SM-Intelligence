package com.smi.identity_service.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One thing that happened on the platform: a sign in, a sign up, an admin change. Never updated. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "detail", length = 4000)
    private String detail;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    protected AuditEvent() {
    }

    public AuditEvent(UUID actorId, String actorEmail, String actorRole, String action, String targetType,
                      String targetId, String targetLabel, String detail, String ipAddress, String outcome) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.outcome = outcome;
    }

    public UUID getId() { return id; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getTargetLabel() { return targetLabel; }
    public String getDetail() { return detail; }
    public String getIpAddress() { return ipAddress; }
    public String getOutcome() { return outcome; }
}
