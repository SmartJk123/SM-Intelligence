package com.smi.identity_service.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An alert for the admin portal's bell and Notifications page, such as a new sign up or a service going down. */
@Entity
@Table(name = "admin_notifications")
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 4000)
    private String message;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    protected AdminNotification() {
    }

    public AdminNotification(String category, String severity, String title, String message) {
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public OffsetDateTime getReadAt() { return readAt; }
}
