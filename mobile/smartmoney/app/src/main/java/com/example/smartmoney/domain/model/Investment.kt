package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable
import org.json.JSONObject
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
 * @property userId Owner user ID.
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
    val userId: String = "",
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

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        json.put("name", name)
        json.put("type", type.name)
        json.put("principalMinor", principalMinor)
        if (currentValueMinor != null) {
            json.put("currentValueMinor", currentValueMinor)
        }
        json.put("valuationDate", valuationDate.toString())
        if (maturityDate != null) {
            json.put("maturityDate", maturityDate.toString())
        }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): Investment {
            val id = json.optString("id", "")
            val userId = json.optString("userId", "")
            val name = json.optString("name", "")
            val typeStr = json.optString("type", "OTHER")
            val type = InvestmentType.fromString(typeStr)
            val principalMinor = json.optLong("principalMinor", 0L)
            val currentValueMinor = if (json.has("currentValueMinor")) json.optLong("currentValueMinor") else null
            val valDateStr = json.optString("valuationDate", "")
            val valuationDate = try {
                if (valDateStr.isNotBlank()) LocalDate.parse(valDateStr) else LocalDate.now()
            } catch (_: Exception) {
                LocalDate.now()
            }
            val matDateStr = json.optString("maturityDate", "")
            val maturityDate = try {
                if (matDateStr.isNotBlank()) LocalDate.parse(matDateStr) else null
            } catch (_: Exception) {
                null
            }

            return Investment(
                id = id,
                userId = userId,
                name = name,
                type = type,
                principalMinor = principalMinor,
                currentValueMinor = currentValueMinor,
                valuationDate = valuationDate,
                maturityDate = maturityDate
            )
        }
    }
}
