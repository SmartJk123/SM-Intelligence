package com.smi.budgets_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A spending limit for one category, recurring monthly. The schema also
 * carries multi-category and multi-account junction tables (budget_categories,
 * budget_accounts) for a richer budget later; this entity deliberately only
 * uses categoryName, a plain string, since categories-service does not exist
 * yet and the rest of the platform treats a category as the bank's own
 * narrative text, not an id from a real category catalogue.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "recurrence", nullable = false)
    private String recurrence = "MONTHLY";

    @Column(name = "alert_threshold_percentage")
    private BigDecimal alertThresholdPercentage = new BigDecimal("85.00");

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(UUID ownerId, String categoryName, BigDecimal allocatedAmount) {
        this.ownerId = ownerId;
        this.categoryName = categoryName;
        this.name = categoryName;
        this.allocatedAmount = allocatedAmount;
        this.startDate = LocalDate.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getCategoryName() { return categoryName; }
    public String getCurrency() { return currency; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getRecurrence() { return recurrence; }
    public BigDecimal getAlertThresholdPercentage() { return alertThresholdPercentage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
