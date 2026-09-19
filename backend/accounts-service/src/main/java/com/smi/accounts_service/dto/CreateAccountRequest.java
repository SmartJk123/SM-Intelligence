package com.smi.accounts_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateAccountRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "providerAccountId is required")
    private String providerAccountId;

    @NotBlank(message = "accountName is required")
    @Size(min = 1, max = 150, message = "accountName must be between 1 and 150 characters")
    private String accountName;

    @NotBlank(message = "institution is required")
    private String institution;

    @NotBlank(message = "accountType is required")
    @Pattern(regexp = "^(DEPOSIT|CREDIT)$", message = "accountType must be DEPOSIT or CREDIT")
    private String accountType;

    @NotBlank(message = "maskedIdentifier is required")
    private String maskedIdentifier;

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    private String currency = "KES";

    private BigDecimal initialBalance = BigDecimal.ZERO;

    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Pattern(regexp = "^(BANK_API|MANUAL)$", message = "dataSource must be BANK_API or MANUAL")
    private String dataSource = "MANUAL";

    public CreateAccountRequest() {
    }

    public CreateAccountRequest(UUID userId, String providerAccountId, String accountName, String institution,
                                String accountType, String maskedIdentifier, String currency,
                                BigDecimal initialBalance, BigDecimal creditLimit, String dataSource) {
        this.userId = userId;
        this.providerAccountId = providerAccountId;
        this.accountName = accountName;
        this.institution = institution;
        this.accountType = accountType;
        this.maskedIdentifier = maskedIdentifier;
        this.currency = currency != null ? currency : "KES";
        this.initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        this.creditLimit = creditLimit != null ? creditLimit : BigDecimal.ZERO;
        this.dataSource = dataSource != null ? dataSource : "MANUAL";
    }

    // Getters and Setters

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

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
}
