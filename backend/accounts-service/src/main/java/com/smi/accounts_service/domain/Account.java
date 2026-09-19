package com.smi.accounts_service.domain;

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
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Account domain entity mapped to the 'accounts' table in the accounts service database.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_account_id", nullable = false)
    private String providerAccountId;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "institution", nullable = false)
    private String institution;

    @Column(name = "account_type", nullable = false)
    private String accountType; // DEPOSIT, CREDIT

    @Column(name = "masked_identifier", nullable = false)
    private String maskedIdentifier;

    @Column(name = "currency", nullable = false, columnDefinition = "bpchar")
    private String currency = "KES";

    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal ledgerBalance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "credit_outstanding", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditOutstanding = BigDecimal.ZERO;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "account_status", nullable = false)
    private String accountStatus = "ACTIVE"; // ACTIVE, CLOSED, RESTRICTED

    @Column(name = "connection_status", nullable = false)
    private String connectionStatus = "CONNECTED"; // CONNECTED, SYNCING, DISCONNECTED, ACTION_REQUIRED

    @Column(name = "last_updated")
    private OffsetDateTime lastUpdated;

    @Column(name = "data_source", nullable = false)
    private String dataSource = "MANUAL"; // BANK_API, MANUAL

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Account() {
    }

    public Account(UUID userId, String providerAccountId, String accountName, String institution,
                   String accountType, String maskedIdentifier, String currency, BigDecimal availableBalance) {
        this.userId = userId;
        this.providerAccountId = providerAccountId;
        this.accountName = accountName;
        this.institution = institution;
        this.accountType = accountType;
        this.maskedIdentifier = maskedIdentifier;
        this.currency = currency != null ? currency : "KES";
        this.availableBalance = availableBalance != null ? availableBalance : BigDecimal.ZERO;
        this.ledgerBalance = this.availableBalance;
        this.lastUpdated = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (lastUpdated == null) {
            lastUpdated = OffsetDateTime.now();
        }
        if (currency == null) {
            currency = "KES";
        }
        if (ledgerBalance == null) {
            ledgerBalance = BigDecimal.ZERO;
        }
        if (availableBalance == null) {
            availableBalance = BigDecimal.ZERO;
        }
        if (creditOutstanding == null) {
            creditOutstanding = BigDecimal.ZERO;
        }
        if (creditLimit == null) {
            creditLimit = BigDecimal.ZERO;
        }
        if (accountStatus == null) {
            accountStatus = "ACTIVE";
        }
        if (connectionStatus == null) {
            connectionStatus = "CONNECTED";
        }
        if (dataSource == null) {
            dataSource = "MANUAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getProviderAccountId() {
        return providerAccountId;
    }

    public void setProviderAccountId(String providerAccountId) {
        this.providerAccountId = providerAccountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getMaskedIdentifier() {
        return maskedIdentifier;
    }

    public void setMaskedIdentifier(String maskedIdentifier) {
        this.maskedIdentifier = maskedIdentifier;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getCreditOutstanding() {
        return creditOutstanding;
    }

    public void setCreditOutstanding(BigDecimal creditOutstanding) {
        this.creditOutstanding = creditOutstanding;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(OffsetDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
