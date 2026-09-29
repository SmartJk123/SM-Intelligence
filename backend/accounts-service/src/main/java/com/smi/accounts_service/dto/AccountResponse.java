package com.smi.accounts_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smi.accounts_service.domain.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String providerAccountId;
    private String accountName;
    private String institution;
    private String accountType;
    private String maskedIdentifier;
    private String currency;
    private BigDecimal ledgerBalance;
    private BigDecimal availableBalance;
    private BigDecimal creditOutstanding;
    private BigDecimal creditLimit;
    private String accountStatus;
    private String connectionStatus;
    private OffsetDateTime lastUpdated;
    private String dataSource;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public AccountResponse() {
    }

    public static AccountResponse fromEntity(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setUserId(account.getUserId());
        response.setProviderAccountId(account.getProviderAccountId());
        response.setAccountName(account.getAccountName());
        response.setInstitution(account.getInstitution());
        response.setAccountType(account.getAccountType());
        response.setMaskedIdentifier(account.getMaskedIdentifier());
        response.setCurrency(account.getCurrency());
        response.setLedgerBalance(account.getLedgerBalance());
        response.setAvailableBalance(account.getAvailableBalance());
        response.setCreditOutstanding(account.getCreditOutstanding());
        response.setCreditLimit(account.getCreditLimit());
        response.setAccountStatus(account.getAccountStatus());
        response.setConnectionStatus(account.getConnectionStatus());
        response.setLastUpdated(account.getLastUpdated());
        response.setDataSource(account.getDataSource());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());
        return response;
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

    @JsonProperty("accountId")
    public String getAccountId() {
        return providerAccountId;
    }

    @JsonProperty("bankName")
    public String getBankName() {
        return institution;
    }
}
