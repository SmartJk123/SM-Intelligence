package com.example.smartmoney

import com.example.smartmoney.domain.model.Investment
import com.example.smartmoney.domain.model.InvestmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InvestmentCalculationTest {

    @Test
    fun evaluates_portfolio_valuation_as_null_when_any_holding_valuation_is_unknown() {
        val holdings = listOf(
            Investment(
                id = "inv1",
                name = "Sanlam MMF",
                type = InvestmentType.MONEY_MARKET,
                principalMinor = 1_000_000L,
                currentValueMinor = 1_045_000L,
                valuationDate = LocalDate.now()
            ),
            Investment(
                id = "inv2",
                name = "Fixed Deposit",
                type = InvestmentType.FIXED_DEPOSIT,
                principalMinor = 500_000L,
                currentValueMinor = null, // Unknown valuation
                valuationDate = LocalDate.now()
            )
        )

        // Web app rule: Valuation unavailable if any currentValueMinor is null
        val portfolioValuation: Long? = if (holdings.any { it.currentValueMinor == null }) {
            null
        } else {
            holdings.sumOf { it.currentValueMinor ?: 0L }
        }

        assertNull("Portfolio valuation must be null when any holding valuation is unknown", portfolioValuation)
    }

    @Test
    fun computes_portfolio_valuation_when_all_valuations_are_recorded() {
        val holdings = listOf(
            Investment(
                id = "inv1",
                name = "Sanlam MMF",
                type = InvestmentType.MONEY_MARKET,
                principalMinor = 1_000_000L,
                currentValueMinor = 1_050_000L,
                valuationDate = LocalDate.now()
            ),
            Investment(
                id = "inv2",
                name = "Treasury Bill",
                type = InvestmentType.TREASURY_BILL,
                principalMinor = 2_000_000L,
                currentValueMinor = 2_120_000L,
                valuationDate = LocalDate.now()
            )
        )

        val portfolioValuation: Long? = if (holdings.any { it.currentValueMinor == null }) {
            null
        } else {
            holdings.sumOf { it.currentValueMinor ?: 0L }
        }

        assertEquals(3_170_000L, portfolioValuation)
    }

    @Test
    fun flags_upcoming_maturity_when_maturity_date_is_within_90_days() {
        val now = LocalDate.now()
        val holdingMaturingSoon = Investment(
            id = "inv3",
            name = "Treasury Bill (91-Day)",
            type = InvestmentType.TREASURY_BILL,
            principalMinor = 2_000_000L,
            currentValueMinor = 2_150_000L,
            valuationDate = now,
            maturityDate = now.plusDays(45)
        )

        val holdingFarFuture = Investment(
            id = "inv4",
            name = "2-Year Bond",
            type = InvestmentType.OTHER,
            principalMinor = 5_000_000L,
            currentValueMinor = 5_200_000L,
            valuationDate = now,
            maturityDate = now.plusDays(365)
        )

        assertTrue(holdingMaturingSoon.isMaturingSoon)
        assertEquals(45L, holdingMaturingSoon.daysUntilMaturity)

        assertFalse(holdingFarFuture.isMaturingSoon)
    }
}
