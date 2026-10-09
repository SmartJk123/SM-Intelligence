package com.smi.investments_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One investment with its latest valuation. currentValue is null until one is recorded. */
public record InvestmentResponse(
        UUID id,
        String name,
        String type,
        String institution,
        String currency,
        BigDecimal principal,
        BigDecimal currentValue,
        LocalDate valuationDate,
        LocalDate maturityDate,
        String status) {
}
