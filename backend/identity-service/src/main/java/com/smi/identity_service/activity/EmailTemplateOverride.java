package com.smi.identity_service.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** An administrator's edit of a built-in email template. Deleting it restores the built-in text. */
@Entity
@Table(name = "email_templates")
public class EmailTemplateOverride {

    @Id
    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, length = 20000)
    private String body;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by")
    private String updatedBy;

    protected EmailTemplateOverride() {
    }

    public EmailTemplateOverride(String templateKey) {
        this.templateKey = templateKey;
    }

    public void update(String subject, String body, String updatedBy) {
        this.subject = subject;
        this.body = body;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getTemplateKey() { return templateKey; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
