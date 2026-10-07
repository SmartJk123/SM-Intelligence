package com.smi.assistant_service.model;

import java.math.BigDecimal;

public class TransactionSummary {

    private String category;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String transactionDate;

    public TransactionSummary() {
    }

    public TransactionSummary(String category, BigDecimal amount, String currency, String description, String transactionDate) {
        this.category = category;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "KES";
        this.description = description;
        this.transactionDate = transactionDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }
}
