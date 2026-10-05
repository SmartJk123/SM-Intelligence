package com.smi.budgets_service.dto;

import com.smi.budgets_service.domain.Budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID userId,
        String category,
        String currency,
        BigDecimal monthlyLimit,
        BigDecimal alertThresholdPercentage,
        String status,
        Instant createdAt) {

    public static BudgetResponse fromEntity(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getOwnerId(),
                budget.getCategoryName(),
                budget.getCurrency(),
                budget.getAllocatedAmount(),
                budget.getAlertThresholdPercentage(),
                budget.getStatus(),
                budget.getCreatedAt());
    }
}
