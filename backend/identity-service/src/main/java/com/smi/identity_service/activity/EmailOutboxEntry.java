package com.smi.identity_service.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A copy of one email the platform tried to send, for the admin Outbox. Links and
 * temporary passwords are stored as [hidden], so reading the outbox never gives
 * an administrator a way into someone else's account.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, length = 20000)
    private String body;

    @Column(name = "template_key")
    private String templateKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error", length = 2000)
    private String error;

    protected EmailOutboxEntry() {
    }

    public EmailOutboxEntry(String recipient, String subject, String body, String templateKey, String status,
                            String error) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.templateKey = templateKey;
        this.status = status;
        this.error = error;
    }

    public UUID getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getTemplateKey() { return templateKey; }
    public String getStatus() { return status; }
    public String getError() { return error; }
}
