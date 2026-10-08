package com.smi.assistant_service.model;

import java.math.BigDecimal;

public class BudgetSummary {

    private String category;
    private BigDecimal limitAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private String currency;

    public BudgetSummary() {
    }

    public BudgetSummary(String category, BigDecimal limitAmount, BigDecimal spentAmount, String currency) {
        this.category = category;
        this.limitAmount = limitAmount != null ? limitAmount : BigDecimal.ZERO;
        this.spentAmount = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        this.remainingAmount = this.limitAmount.subtract(this.spentAmount);
        this.currency = currency != null ? currency : "KES";
    }

    public BudgetSummary(String category, BigDecimal limitAmount, BigDecimal spentAmount, BigDecimal remainingAmount, String currency) {
        this.category = category;
        this.limitAmount = limitAmount != null ? limitAmount : BigDecimal.ZERO;
        this.spentAmount = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        this.remainingAmount = remainingAmount != null ? remainingAmount : this.limitAmount.subtract(this.spentAmount);
        this.currency = currency != null ? currency : "KES";
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
        if (this.spentAmount != null) {
            this.remainingAmount = this.limitAmount.subtract(this.spentAmount);
        }
    }

    public BigDecimal getSpentAmount() {
        return spentAmount;
    }

    public void setSpentAmount(BigDecimal spentAmount) {
        this.spentAmount = spentAmount;
        if (this.limitAmount != null) {
            this.remainingAmount = this.limitAmount.subtract(this.spentAmount);
        }
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
