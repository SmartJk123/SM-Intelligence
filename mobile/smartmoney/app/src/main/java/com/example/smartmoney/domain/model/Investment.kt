package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class InvestmentType(val displayName: String) {
    MONEY_MARKET("Money Market"),
    FIXED_DEPOSIT("Fixed Deposit"),
    TREASURY_BILL("Treasury Bill"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): InvestmentType =
            entries.find { it.displayName.equals(value, ignoreCase = true) }
                ?: entries.find { it.name.equals(value, ignoreCase = true) }
                ?: OTHER
    }
}

/**
 * Pure domain model representing an investment holding record.
 *
 * @property id Unique identifier.
 * @property name Investment instrument name (e.g., "Sanlam MMF", "91-Day Treasury Bill").
 * @property type Type of investment asset.
 * @property principalMinor Original invested amount in integer cents.
 * @property currentValueMinor Current valuation in integer cents (null if unknown or unrecorded).
 * @property valuationDate Date the valuation was recorded.
 * @property maturityDate Maturity date for fixed-term instruments (null for open-ended funds like MMF).
 */
@Immutable
data class Investment(
    val id: String,
    val name: String,
    val type: InvestmentType,
    val principalMinor: Long,
    val currentValueMinor: Long? = null,
    val valuationDate: LocalDate,
    val maturityDate: LocalDate? = null
) {
    val principalMajor: Double
        get() = principalMinor / 100.0

    val currentValueMajor: Double?
        get() = currentValueMinor?.let { it / 100.0 }

    val gainOrLossMinor: Long?
        get() = currentValueMinor?.let { it - principalMinor }

    val gainOrLossMajor: Double?
        get() = gainOrLossMinor?.let { it / 100.0 }

    val daysUntilMaturity: Long?
        get() = maturityDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    val isMatured: Boolean
        get() = daysUntilMaturity?.let { it < 0 } ?: false

    val isMaturingSoon: Boolean
        get() = daysUntilMaturity?.let { it in 0..90 } ?: false
}
