package com.example.smartmoney.core.sms.parser

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.smartmoney.core.sms.model.MpesaTransactionKind
import com.example.smartmoney.core.sms.model.ParsedFinancialMovement
import com.example.smartmoney.core.sms.model.TransactionDirection
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses a limited set of recognizable M-PESA confirmation-message templates.
 *
 * SMS templates can change and differ by transaction type. Treat this parser as a conservative
 * first version: unsupported or ambiguous messages return null instead of guessing.
 *
 * Sender validation, permissions, deduplication, persistence, and balance updates belong to
 * other layers and must not be done here.
 */
class MpesaTransactionParser {

    @RequiresApi(Build.VERSION_CODES.O)
    fun parse(message: String): ParsedFinancialMovement? {
        val text = message.trim()
        if (text.isEmpty()) return null

        val reference = REFERENCE_REGEX.find(text)?.groupValues?.get(1) ?: return null
        if (!text.contains("confirmed", ignoreCase = true)) return null

        val amount = AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.toMoney() ?: return null

        // Extract primary (M-PESA) and secondary (M-Shwari) balances accurately
        val mpesaBalance = MPESA_BALANCE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        val mshwariBalance = MSHWARI_BALANCE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        val genericBalance = if (mpesaBalance == null && mshwariBalance == null) {
            GENERIC_BALANCE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        } else null

        // Primary wallet balance is strictly the M-PESA balance (or generic balance if no M-PESA tag is specified)
        val balance = mpesaBalance ?: genericBalance
        val secondaryBalance = mshwariBalance

        val fee = FEE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        val dateTime = parseDateTime(text)

        val lower = text.lowercase(Locale.ROOT)

        val details = when {
            isReversal(lower) -> TransactionDetails(
                kind = MpesaTransactionKind.REVERSAL,
                direction = TransactionDirection.CREDIT,
                reversedReference = ORIGINAL_REFERENCE_REGEX.find(text)?.groupValues?.get(1),
            )

            // Money returned/withdrawn from M-Shwari to the M-PESA wallet.
            isMshwariToMpesa(lower) -> TransactionDetails(
                kind = MpesaTransactionKind.MSHWARI_TO_MPESA,
                direction = TransactionDirection.CREDIT,
                counterparty = "M-Shwari",
                accountReference = "M-Shwari Account",
            )

            // Money transferred/deposited from the M-PESA wallet to M-Shwari.
            isMpesaToMshwari(lower) -> TransactionDetails(
                kind = MpesaTransactionKind.MPESA_TO_MSHWARI,
                direction = TransactionDirection.DEBIT,
                counterparty = "M-Shwari",
                accountReference = "M-Shwari Account",
            )

            isReceivedFromBank(lower) -> TransactionDetails(
                kind = MpesaTransactionKind.BANK_TO_MPESA,
                direction = TransactionDirection.CREDIT,
                counterparty = namedParty(text, listOf("from")),
            )

            isMpesaToBank(lower) -> TransactionDetails(
                kind = MpesaTransactionKind.MPESA_TO_BANK,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("to")),
                accountReference = ACCOUNT_REFERENCE_REGEX.find(text)?.groupValues?.get(1),
            )

            lower.contains("received") && (lower.contains(" from ") || lower.contains(" from:")) -> TransactionDetails(
                kind = MpesaTransactionKind.RECEIVE_MONEY,
                direction = TransactionDirection.CREDIT,
                counterparty = namedParty(text, listOf("from")),
                counterpartyPhone = PHONE_REGEX.find(text)?.value,
            )

            lower.contains("sent to") || lower.contains("send money to") -> TransactionDetails(
                kind = MpesaTransactionKind.SEND_MONEY,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("sent to", "send money to")),
                counterpartyPhone = PHONE_REGEX.find(text)?.value,
            )

            lower.contains("paybill") || (lower.contains("paid to") && lower.contains("account")) -> TransactionDetails(
                kind = MpesaTransactionKind.PAYBILL,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("paid to", "paybill")),
                accountReference = ACCOUNT_REFERENCE_REGEX.find(text)?.groupValues?.get(1),
            )

            (lower.contains("bought") && (lower.contains("goods") || lower.contains("till"))) ||
                    lower.contains("buy goods") -> TransactionDetails(
                kind = MpesaTransactionKind.BUY_GOODS,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("at", "to")),
            )

            lower.contains("withdraw") && lower.contains("atm") -> TransactionDetails(
                kind = MpesaTransactionKind.ATM_WITHDRAWAL,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("from")),
            )

            lower.contains("withdraw") && (lower.contains("agent") || lower.contains("agent no")) -> TransactionDetails(
                kind = MpesaTransactionKind.AGENT_WITHDRAWAL,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("from")),
            )

            lower.contains("fuliza") -> TransactionDetails(
                kind = MpesaTransactionKind.FULIZA,
                direction = if (lower.contains("received") || lower.contains("disbursed")) {
                    TransactionDirection.CREDIT
                } else {
                    TransactionDirection.DEBIT
                },
            )

            lower.contains("airtime") || lower.contains("top up") || lower.contains("top-up") -> TransactionDetails(
                kind = MpesaTransactionKind.AIRTIME,
                direction = TransactionDirection.DEBIT,
                counterparty = namedParty(text, listOf("to")),
            )

            else -> return null
        }

        return ParsedFinancialMovement(
            reference = reference,
            amount = amount,
            direction = details.direction,
            kind = details.kind,
            counterparty = details.counterparty,
            counterpartyPhone = details.counterpartyPhone,
            accountReference = details.accountReference,
            balanceAfter = balance,
            secondaryBalanceAfter = secondaryBalance,
            fee = fee,
            transactionDateTime = dateTime,
            reversedReference = details.reversedReference,
        )
    }

    private fun isReversal(lower: String): Boolean =
        lower.contains("reversal") || lower.contains("reversed")

    private fun isMshwariToMpesa(lower: String): Boolean =
        (lower.contains("m-shwari") || lower.contains("mshwari")) && (
            lower.contains("from m-shwari") ||
            lower.contains("from mshwari") ||
            lower.contains("from your m-shwari") ||
            lower.contains("withdrawn from m-shwari") ||
            lower.contains("withdrawn from your m-shwari") ||
            lower.contains("transferred from m-shwari") ||
            lower.contains("transferred from your m-shwari") ||
            lower.contains("returned from m-shwari") ||
            lower.contains("received from m-shwari")
        )

    private fun isMpesaToMshwari(lower: String): Boolean =
        (lower.contains("m-shwari") || lower.contains("mshwari")) && (
            lower.contains("to m-shwari") ||
            lower.contains("to mshwari") ||
            lower.contains("to your m-shwari") ||
            lower.contains("transferred to m-shwari") ||
            lower.contains("transferred to your m-shwari") ||
            lower.contains("deposited to m-shwari") ||
            lower.contains("deposited to your m-shwari") ||
            lower.contains("sent to m-shwari") ||
            lower.contains("paid to m-shwari")
        )

    private fun isReceivedFromBank(lower: String): Boolean =
        (lower.contains("received") || lower.contains("credited")) &&
                (lower.contains("bank") || lower.contains("from account"))

    private fun isMpesaToBank(lower: String): Boolean =
        lower.contains("sent to bank") || lower.contains("transferred to bank") ||
                lower.contains("to your bank account")

    private fun namedParty(text: String, markers: List<String>): String? {
        for (marker in markers) {
            val markerIndex = text.indexOf(marker, ignoreCase = true)
            if (markerIndex < 0) continue

            var value = text.substring(markerIndex + marker.length)
                .trimStart(' ', ':', '-')
                .substringBeforeDateOrBalance()
                .substringBefore(ACCOUNT_KEYWORD_REGEX)
                .trim(' ', '.', ',', ':', '-')

            // Remove a phone number from the end of the party label; it is captured separately.
            value = value.replace(PHONE_REGEX, "").trim(' ', '.', ',', ':', '-')
            if (value.isNotBlank()) return value.take(MAX_PARTY_LENGTH)
        }
        return null
    }

    private fun String.substringBeforeDateOrBalance(): String {
        val dateMatch = DATE_TIME_REGEX.find(this)
        val balanceMatch = BALANCE_REGEX.find(this)
        val stopIndex = listOfNotNull(dateMatch?.range?.first, balanceMatch?.range?.first).minOrNull()
        return if (stopIndex == null) this else substring(0, stopIndex)
    }

    private fun String.substringBefore(regex: Regex): String {
        val match = regex.find(this) ?: return this
        return substring(0, match.range.first)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDateTime(text: String): LocalDateTime? {
        val match = DATE_TIME_REGEX.find(text) ?: return null
        val value = match.value.trim().replace(WHITE_SPACE_REGEX, " ").uppercase(Locale.ENGLISH)
        val normalized = value.replace(Regex("""(\d{1,2}:\d{2})\s*([AP]M)"""), "$1 $2")
        for (formatter in DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next supported format.
            }
        }
        return null
    }

    private fun String.toMoney(): BigDecimal? =
        replace(",", "").toBigDecimalOrNull()

    private data class TransactionDetails(
        val kind: MpesaTransactionKind,
        val direction: TransactionDirection,
        val counterparty: String? = null,
        val counterpartyPhone: String? = null,
        val accountReference: String? = null,
        val reversedReference: String? = null,
    )

    private companion object {
        const val MAX_PARTY_LENGTH = 100

        val WHITE_SPACE_REGEX = Regex("""\s+""")

        val ACCOUNT_KEYWORD_REGEX = Regex(
            pattern = """\b(?:account|account number|till number|paybill number)\b""",
            option = RegexOption.IGNORE_CASE,
        )

        val REFERENCE_REGEX = Regex(
            pattern = """\b([A-Z0-9]{8,12})[:\s]+Confirmed\b""",
            option = RegexOption.IGNORE_CASE,
        )
        val AMOUNT_REGEX = Regex(
            pattern = """(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val MPESA_BALANCE_REGEX = Regex(
            pattern = """(?:new\s+)?M-PESA\s+(?:account\s+)?balance\s+is\s*(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val MSHWARI_BALANCE_REGEX = Regex(
            pattern = """(?:new\s+)?M-Shwari\s+(?:(?:savings?|account|lock|wallet|deposit)\s+){0,3}balance\s+is\s*(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val GENERIC_BALANCE_REGEX = Regex(
            pattern = """(?:new\s+)?balance\s+is\s*(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val BALANCE_REGEX = Regex(
            pattern = """(?:(?:new\s+)?M-PESA\s+(?:(?:account|wallet)\s+)?balance\s+is|(?:new\s+)?M-Shwari\s+(?:(?:savings?|account|lock|wallet|deposit)\s+){0,3}balance\s+is|balance\s+is)\s*(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val FEE_REGEX = Regex(
            pattern = """(?:transaction\s+cost|transaction\s+fee|fee)\s*,?\s*(?:is\s*)?(?:Ksh|KES)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            option = RegexOption.IGNORE_CASE,
        )
        val DATE_TIME_REGEX = Regex(
            pattern = """\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s+(?:at\s+)?\d{1,2}:\d{2}\s*[AP]M\b""",
            option = RegexOption.IGNORE_CASE,
        )
        val PHONE_REGEX = Regex("""(?<!\d)(?:\+?254|0)[17]\d{8}(?!\d)""")
        val ACCOUNT_REFERENCE_REGEX = Regex(
            pattern = """(?:account(?:\s+number)?|account\s+no\.?|paybill(?:\s+number)?|till(?:\s+number)?)\s*[:#-]?\s*([A-Z0-9-]{2,30})""",
            option = RegexOption.IGNORE_CASE,
        )
        val ORIGINAL_REFERENCE_REGEX = Regex(
            pattern = """(?:original\s+transaction|transaction)\s+(?:reference|code)\s*[:#-]?\s*([A-Z0-9]{8,12})""",
            option = RegexOption.IGNORE_CASE,
        )

        @RequiresApi(Build.VERSION_CODES.O)
        val DATE_FORMATTERS = listOf(
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yy 'at' h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy 'at' h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy 'at' h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy h:mm a").toFormatter(Locale.ENGLISH),
        )
    }
}
