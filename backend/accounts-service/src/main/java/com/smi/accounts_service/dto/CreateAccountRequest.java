package com.smi.accounts_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateAccountRequest {

    @NotNull(message = "userId is required")
    @JsonAlias({"user_id"})
    private UUID userId;

    @NotBlank(message = "providerAccountId is required")
    @JsonAlias({"provider_account_id", "accountId", "account_id", "accountNumber", "account_number", "id"})
    private String providerAccountId;

    @NotBlank(message = "accountName is required")
    @Size(min = 1, max = 150, message = "accountName must be between 1 and 150 characters")
    @JsonAlias({"account_name", "name"})
    private String accountName;

    @NotBlank(message = "institution is required")
    @JsonAlias({"bank", "bankName", "bank_name", "institution_name"})
    private String institution;

    @Pattern(regexp = "(?i)^(DEPOSIT|CREDIT|DEBIT|SAVINGS|CHECKING)$", message = "accountType must be DEPOSIT, CREDIT, DEBIT, SAVINGS, or CHECKING")
    @JsonAlias({"account_type", "cardType", "card_type"})
    private String accountType = "DEPOSIT";

    @JsonAlias({"masked_identifier", "maskedAccountNumber", "masked_account_number"})
    private String maskedIdentifier;

    @Pattern(regexp = "(?i)^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    private String currency = "KES";

    @JsonAlias({"initial_balance", "balance", "availableBalance", "available_balance", "amount"})
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @JsonAlias({"credit_limit"})
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Pattern(regexp = "(?i)^(BANK_API|MANUAL)$", message = "dataSource must be BANK_API or MANUAL")
    @JsonAlias({"data_source"})
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
        if ((this.maskedIdentifier == null || this.maskedIdentifier.isBlank()) && providerAccountId != null && !providerAccountId.isBlank()) {
            String trimmed = providerAccountId.trim();
            this.maskedIdentifier = trimmed.length() > 4 ? "**** " + trimmed.substring(trimmed.length() - 4) : "**** " + trimmed;
        }
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
        if (accountType != null) {
            String upper = accountType.trim().toUpperCase();
            if (upper.equals("DEBIT") || upper.equals("SAVINGS") || upper.equals("CHECKING")) {
                return "DEPOSIT";
            }
            return upper;
        }
        return "DEPOSIT";
    }

    public void setAccountType(String accountType) {
        if (accountType != null) {
            String upper = accountType.trim().toUpperCase();
            if (upper.equals("DEBIT") || upper.equals("SAVINGS") || upper.equals("CHECKING")) {
                this.accountType = "DEPOSIT";
            } else {
                this.accountType = upper;
            }
        } else {
            this.accountType = "DEPOSIT";
        }
    }

    public String getMaskedIdentifier() {
        if ((maskedIdentifier == null || maskedIdentifier.isBlank()) && providerAccountId != null && !providerAccountId.isBlank()) {
            String trimmed = providerAccountId.trim();
            return trimmed.length() > 4 ? "**** " + trimmed.substring(trimmed.length() - 4) : "**** " + trimmed;
        }
        return maskedIdentifier != null && !maskedIdentifier.isBlank() ? maskedIdentifier : "****";
    }

    public void setMaskedIdentifier(String maskedIdentifier) {
        this.maskedIdentifier = maskedIdentifier;
    }

    public String getCurrency() {
        return currency != null ? currency.toUpperCase() : "KES";
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
