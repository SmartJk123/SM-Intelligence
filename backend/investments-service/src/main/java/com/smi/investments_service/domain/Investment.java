package com.smi.investments_service.domain;

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
 * One holding a customer recorded: a money market fund, a fixed deposit, a
 * treasury bill and so on. The amount put in is kept as contributions; what it
 * is worth now lives in {@link InvestmentValuation}, one row per valuation day.
 */
@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "institution", nullable = false)
    private String institution;

    // CHAR(3) in V1__initial_schema.sql; without the type code Hibernate expects VARCHAR(3).
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency = "KES";

    /** FIXED_DEPOSIT, TREASURY, FUND, EQUITY or OTHER (the schema's check constraint). */
    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "contributions", nullable = false, precision = 19, scale = 4)
    private BigDecimal contributions = BigDecimal.ZERO;

    @Column(name = "principal_withdrawn", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalWithdrawn = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Investment() {
    }

    public Investment(UUID ownerId) {
        this.ownerId = ownerId;
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
    public void setName(String name) { this.name = name; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public String getCurrency() { return currency; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public BigDecimal getContributions() { return contributions; }
    public void setContributions(BigDecimal contributions) { this.contributions = contributions; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public String getStatus() { return status; }
}
