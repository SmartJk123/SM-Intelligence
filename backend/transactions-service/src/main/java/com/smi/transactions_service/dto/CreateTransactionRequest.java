package com.smi.transactions_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateTransactionRequest {

    @NotNull(message = "accountId is required")
    private UUID accountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    private String currency = "KES";

    @NotBlank(message = "transactionType is required")
    @Pattern(regexp = "^(CREDIT|DEBIT)$", message = "transactionType must be CREDIT or DEBIT")
    private String transactionType;

    private String counterparty;

    private String paymentMethod;

    private String providerReference;

    private String description;

    private UUID categoryId;

    private OffsetDateTime transactionDate;

    public CreateTransactionRequest() {
    }

    public CreateTransactionRequest(UUID accountId, BigDecimal amount, String currency, String transactionType,
                                  String counterparty, String paymentMethod, String providerReference,
                                  String description, UUID categoryId, OffsetDateTime transactionDate) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency != null ? currency : "KES";
        this.transactionType = transactionType;
        this.counterparty = counterparty;
        this.paymentMethod = paymentMethod;
        this.providerReference = providerReference;
        this.description = description;
        this.categoryId = categoryId;
        this.transactionDate = transactionDate != null ? transactionDate : OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public OffsetDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(OffsetDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }
}
