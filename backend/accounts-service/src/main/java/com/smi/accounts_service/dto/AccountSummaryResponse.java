package com.smi.accounts_service.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AccountSummaryResponse(
        BigDecimal netWorth,
        BigDecimal totalDeposits,
        BigDecimal totalCreditDebt,
        BigDecimal totalCreditLimit,
        BigDecimal totalAvailableCredit,
        int totalAccounts,
        int activeAccounts,
        String currency,
        Map<String, BigDecimal> institutionBreakdown,
        Map<String, BigDecimal> typeBreakdown
) {}
