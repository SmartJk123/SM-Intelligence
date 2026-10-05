package com.smi.budgets_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateBudgetRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "category is required")
    private String category;

    @NotNull(message = "monthlyLimit is required")
    @DecimalMin(value = "0", inclusive = true, message = "monthlyLimit must be zero or more")
    private BigDecimal monthlyLimit;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(BigDecimal monthlyLimit) { this.monthlyLimit = monthlyLimit; }
}
