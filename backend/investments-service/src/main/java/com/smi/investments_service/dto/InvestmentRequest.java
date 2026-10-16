package com.smi.investments_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What the customer types for one investment.
 *
 * @param type          Money Market, Fixed Deposit, Treasury Bill, Equity or Other
 * @param principal     the amount put in
 * @param currentValue  what it is worth on valuationDate; null when not known yet
 * @param institution   who holds it; "Not specified" when left out
 */
public record InvestmentRequest(
        UUID ownerId,
        String name,
        String type,
        BigDecimal principal,
        BigDecimal currentValue,
        LocalDate valuationDate,
        LocalDate maturityDate,
        String institution) {
}
