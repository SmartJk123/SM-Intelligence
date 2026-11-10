package com.example.smartmoney.core.sms.parser

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.smartmoney.core.sms.model.BankInstitution
import com.example.smartmoney.core.sms.model.BankTransactionKind
import com.example.smartmoney.core.sms.model.ParsedBankMovement
import com.example.smartmoney.core.sms.model.TransactionDirection
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parser for commercial bank SMS notifications, specializing in KCB Bank and NCBA Bank transaction syntax.
 *
 * Responsibilities:
 * 1. Strictly discards non-financial alerts (OTPs, activation codes, security warnings).
 * 2. Identifies institution identity (KCB vs NCBA).
 * 3. Extracts reference code, account identifier (masked or unmasked), amount, direction, balance, fees, and timestamps.
 * 4. Normalizes parsed attributes into [ParsedBankMovement].
 */
class BankTransactionParser {

    @RequiresApi(Build.VERSION_CODES.O)
    fun parse(message: String): ParsedBankMovement? {
        val text = message.trim()
        if (text.isEmpty()) return null

        // 1. Strict security check: discard OTPs, activation codes, and security warnings immediately
        if (isNonFinancialSecurityMessage(text)) {
            return null
        }

        // 2. Identify institution and route to appropriate parser
        val lower = text.lowercase(Locale.ROOT)
        return when {
            isKcbMessage(text, lower) -> parseKcb(text, lower)
            isNcbaMessage(text, lower) -> parseNcba(text, lower)
            else -> null
        }
    }

    /**
     * Checks if the message is a non-financial message (e.g., OTP, activation code, security alert).
     */
    private fun isNonFinancialSecurityMessage(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("activation code") ||
            lower.contains("verification code") ||
            lower.contains("valid for only") ||
            lower.contains("kaa chonjo") ||
            (lower.contains("otp") && !lower.contains("debited") && !lower.contains("credited")) ||
            (lower.contains("do not share") && !lower.contains("debited") && !lower.contains("credited") && !lower.contains("sent from"))
        ) {
            return true
        }
        return false
    }

    private fun isKcbMessage(text: String, lower: String): Boolean {
        return lower.contains("kcb") ||
                lower.contains("mbn") ||
                lower.contains("pay to m-pesa biller") ||
                (lower.contains("sent from") && text.contains(Regex("""\b130\*+\d{3}\b""")))
    }

    private fun isNcbaMessage(text: String, lower: String): Boolean {
        return lower.contains("ncba") ||
                (lower.contains("acc 10*") && lower.contains("mpesa ref"))
    }

    // ============================================================================
    // KCB BANK PARSER
    // ============================================================================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseKcb(text: String, lower: String): ParsedBankMovement? {
        // Pattern 1: Modern KCB App / P2P Outflow
        // "MBNHE344446SVLOD  KES 90.00 sent from 130****167 to 254****445 - WILLYS OLUOCH ODUOR OTIENO on 2026-11-04 06:15:25 PM. Cost KES 0.00 Incl. Tax. M-PESA REF: UK44G1RJB8"
        KCB_P2P_OUTFLOW_REGEX.find(text)?.let { match ->
            val ref = match.groupValues[1]
            val amount = match.groupValues[2].toMoney() ?: return null
            val account = match.groupValues[3]
            val counterpartyPhone = match.groupValues[4]
            val counterpartyName = match.groupValues[5].trim()
            val dateStr = match.groupValues[6]
            val costStr = match.groupValues.getOrNull(7)
            val mpesaRef = match.groupValues.getOrNull(8)

            return ParsedBankMovement(
                reference = ref,
                institution = BankInstitution.KCB,
                accountIdentifier = account,
                maskedSuffix = extractMaskedSuffix(account),
                amount = amount,
                direction = TransactionDirection.DEBIT,
                kind = BankTransactionKind.TRANSFER_OUT,
                fee = costStr?.toMoney(),
                counterparty = counterpartyName.ifBlank { null },
                counterpartyPhone = counterpartyPhone.ifBlank { null },
                narration = "Transfer to $counterpartyName",
                transactionDateTime = parseDateTime(dateStr),
                bridgeMpesaReference = mpesaRef?.ifBlank { null }
            )
        }

        // Pattern 2: Modern KCB Pay to M-PESA Biller
        // "MBNHE34QJJVZJZOR Completed. Your Pay to M-PESA Biller request of KES 1,200.00 from 130****167 to 247247 - Equity Paybill Account at 2026-11-05 06:10:17 PM has been processed successfully. Transaction cost KES 28.00 Excl. Tax Amount KES 1.50. MPESA REF:UK5UH7N0HD"
        KCB_BILLER_OUTFLOW_REGEX.find(text)?.let { match ->
            val ref = match.groupValues[1]
            val amount = match.groupValues[2].toMoney() ?: return null
            val account = match.groupValues[3]
            val destination = match.groupValues[4].trim()
            val dateStr = match.groupValues[5]
            val costStr = match.groupValues.getOrNull(6)
            val mpesaRef = match.groupValues.getOrNull(7)

            return ParsedBankMovement(
                reference = ref,
                institution = BankInstitution.KCB,
                accountIdentifier = account,
                maskedSuffix = extractMaskedSuffix(account),
                amount = amount,
                direction = TransactionDirection.DEBIT,
                kind = BankTransactionKind.POS_PURCHASE,
                fee = costStr?.toMoney(),
                counterparty = destination.ifBlank { null },
                narration = "Paybill to $destination",
                transactionDateTime = parseDateTime(dateStr),
                bridgeMpesaReference = mpesaRef?.ifBlank { null }
            )
        }

        // Pattern 3: Modern KCB Inflow (Credit)
        // "MBNHE34QJJVZJZOR KES 5,000.00 received in 130****167 from JOHN DOE on 2026-11-04 10:15:20 AM. M-PESA REF: UK44G1RJB8"
        KCB_MODERN_INFLOW_REGEX.find(text)?.let { match ->
            val ref = match.groupValues[1]
            val amount = match.groupValues[2].toMoney() ?: return null
            val account = match.groupValues[3]
            val sender = match.groupValues[4].trim()
            val dateStr = match.groupValues[5]
            val mpesaRef = match.groupValues.getOrNull(6)

            return ParsedBankMovement(
                reference = ref,
                institution = BankInstitution.KCB,
                accountIdentifier = account,
                maskedSuffix = extractMaskedSuffix(account),
                amount = amount,
                direction = TransactionDirection.CREDIT,
                kind = BankTransactionKind.TRANSFER_IN,
                counterparty = sender.ifBlank { null },
                narration = "Received from $sender",
                transactionDateTime = parseDateTime(dateStr),
                bridgeMpesaReference = mpesaRef?.ifBlank { null }
            )
        }

        // Pattern 4: Classic KCB Core Alert (Debited / Credited)
        // "Dear Customer, your A/C 1234567890 has been debited by KES 2,500.00 on 24-OCT-2026 14:35:10 by Mobile Banking transfer to Jane Doe. Ref: KCB12345678. Available Balance is KES 45,200.00."
        if (lower.contains("debited") || lower.contains("credited")) {
            return parseKcbClassicAlert(text, lower)
        }

        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseKcbClassicAlert(text: String, lower: String): ParsedBankMovement? {
        val isCredit = lower.contains("credited")
        val isDebit = lower.contains("debited")
        if (!isCredit && !isDebit) return null

        val amount = AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.toMoney() ?: return null
        val account = ACCOUNT_NUMBER_REGEX.find(text)?.groupValues?.get(1) ?: return null
        val balance = AVAILABLE_BALANCE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        val ref = REFERENCE_EXPLICIT_REGEX.find(text)?.groupValues?.get(1)
            ?: MBN_REFERENCE_REGEX.find(text)?.groupValues?.get(1)
            ?: generateFallbackRef(account, amount, text)

        val dateStr = DATE_TIME_GENERIC_REGEX.find(text)?.value
        val dateTime = dateStr?.let { parseDateTime(it) }

        // Context / Narration extraction
        val narration = extractContextBetween(
            text = text,
            startMarkers = listOf("by", "for", "Txn:", "Desc:"),
            endMarkers = listOf("Ref:", "Ref", "Txn:", "Available Balance", "New Balance")
        )

        return ParsedBankMovement(
            reference = ref,
            institution = BankInstitution.KCB,
            accountIdentifier = account,
            maskedSuffix = extractMaskedSuffix(account),
            amount = amount,
            direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
            kind = if (isCredit) BankTransactionKind.TRANSFER_IN else BankTransactionKind.TRANSFER_OUT,
            balanceAfter = balance,
            narration = narration,
            transactionDateTime = dateTime
        )
    }

    // ============================================================================
    // NCBA BANK PARSER
    // ============================================================================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseNcba(text: String, lower: String): ParsedBankMovement? {
        // Pattern 1: Modern NCBA Inflow / Received (Provided by User)
        // "Dear JOY WANJIRU your transaction of KES 1,000.00 to JOY WANJIRU Acc 10******64 has been received with Mpesa Ref UJ2BW8L723 on 02/10/2026 10:31 PM. NCBA, Go for it."
        NCBA_RECEIVED_REGEX.find(text)?.let { match ->
            val customerName = match.groupValues[1].trim()
            val amount = match.groupValues[2].toMoney() ?: return null
            val targetName = match.groupValues[3].trim()
            val account = match.groupValues[4]
            val ref = match.groupValues[5]
            val dateStr = match.groupValues[6]

            return ParsedBankMovement(
                reference = ref,
                institution = BankInstitution.NCBA,
                accountIdentifier = account,
                maskedSuffix = extractMaskedSuffix(account),
                amount = amount,
                direction = TransactionDirection.CREDIT,
                kind = BankTransactionKind.TRANSFER_IN,
                counterparty = if (targetName.isNotBlank() && targetName != customerName) targetName else customerName,
                narration = "Inflow received via M-PESA ($ref)",
                transactionDateTime = parseDateTime(dateStr),
                bridgeMpesaReference = ref
            )
        }

        // Pattern 2: Modern NCBA Outflow / Processed (Provided by User)
        // "Dear JOY WANJIRU your transaction of KES 1,000.00 from JOY WANJIRU Acc 10******64 has been processed with Mpesa Ref UJ2BW8LBCF on 02/10/2026 10:48 PM. NCBA, Go for it."
        NCBA_PROCESSED_REGEX.find(text)?.let { match ->
            val customerName = match.groupValues[1].trim()
            val amount = match.groupValues[2].toMoney() ?: return null
            val sourceName = match.groupValues[3].trim()
            val account = match.groupValues[4]
            val ref = match.groupValues[5]
            val dateStr = match.groupValues[6]

            return ParsedBankMovement(
                reference = ref,
                institution = BankInstitution.NCBA,
                accountIdentifier = account,
                maskedSuffix = extractMaskedSuffix(account),
                amount = amount,
                direction = TransactionDirection.DEBIT,
                kind = BankTransactionKind.TRANSFER_OUT,
                counterparty = if (sourceName.isNotBlank()) sourceName else customerName,
                narration = "Outflow processed via M-PESA ($ref)",
                transactionDateTime = parseDateTime(dateStr),
                bridgeMpesaReference = ref
            )
        }

        // Pattern 3: Classic NCBA Core Alert (Debited / Credited)
        // "Dear Customer, Your A/C 1234567890 has been debited with KES 3,500.00 on 23/10/2026. Desc: Internet payment SAFARICOM HOME. Available Balance is KES 82,650.00. Regards NCBA."
        if (lower.contains("debited") || lower.contains("credited")) {
            return parseNcbaClassicAlert(text, lower)
        }

        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseNcbaClassicAlert(text: String, lower: String): ParsedBankMovement? {
        val isCredit = lower.contains("credited")
        val isDebit = lower.contains("debited")
        if (!isCredit && !isDebit) return null

        val amount = AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.toMoney() ?: return null
        val account = ACCOUNT_NUMBER_REGEX.find(text)?.groupValues?.get(1) ?: return null
        val balance = AVAILABLE_BALANCE_REGEX.find(text)?.groupValues?.get(1)?.toMoney()
        val ref = REFERENCE_EXPLICIT_REGEX.find(text)?.groupValues?.get(1)
            ?: MPESA_REF_REGEX.find(text)?.groupValues?.get(1)
            ?: generateFallbackRef(account, amount, text)

        val dateStr = DATE_TIME_GENERIC_REGEX.find(text)?.value
        val dateTime = dateStr?.let { parseDateTime(it) }

        val narration = extractContextBetween(
            text = text,
            startMarkers = listOf("Desc:", "desc:", "for", "by"),
            endMarkers = listOf("Available Balance", "New Available Balance", "New Balance", "Regards", "Ref:")
        )

        return ParsedBankMovement(
            reference = ref,
            institution = BankInstitution.NCBA,
            accountIdentifier = account,
            maskedSuffix = extractMaskedSuffix(account),
            amount = amount,
            direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
            kind = if (isCredit) BankTransactionKind.TRANSFER_IN else BankTransactionKind.TRANSFER_OUT,
            balanceAfter = balance,
            narration = narration,
            transactionDateTime = dateTime
        )
    }

    // ============================================================================
    // HELPER FUNCTIONS & REGEX PATTERNS
    // ============================================================================

    private fun extractMaskedSuffix(account: String): String? {
        val clean = account.trim()
        val trailingDigits = clean.takeLastWhile { it.isDigit() }
        return if (trailingDigits.length in 2..6) trailingDigits else clean.takeLast(4)
    }

    private fun generateFallbackRef(account: String, amount: BigDecimal, text: String): String {
        val cleanAcc = account.replace("*", "X")
        val hash = (text.hashCode().toLong() and 0x7FFFFFFF).toString(36).uppercase(Locale.ROOT)
        return "TXN_${cleanAcc}_${amount.toPlainString().replace(".", "")}_$hash".take(30)
    }

    private fun extractContextBetween(text: String, startMarkers: List<String>, endMarkers: List<String>): String? {
        for (start in startMarkers) {
            val startIndex = text.indexOf(start, ignoreCase = true)
            if (startIndex < 0) continue

            val afterStart = text.substring(startIndex + start.length).trimStart(' ', ':', '-')
            var stopIndex = afterStart.length
            for (end in endMarkers) {
                val eIdx = afterStart.indexOf(end, ignoreCase = true)
                if (eIdx in 0 until stopIndex) {
                    stopIndex = eIdx
                }
            }

            val extracted = afterStart.substring(0, stopIndex).trim(' ', '.', ',', ':', '-')
            if (extracted.isNotBlank()) return extracted.take(100)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDateTime(text: String): LocalDateTime? {
        val cleaned = text.trim().replace(Regex("""\s+"""), " ").uppercase(Locale.ENGLISH)
        val normalized = cleaned.replace(Regex("""(\d{1,2}:\d{2}(?::\d{2})?)\s*([AP]M)"""), "$1 $2")
        for (formatter in DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter)
            } catch (_: DateTimeParseException) {
                // Try next pattern
            }
        }
        return null
    }

    private fun String.toMoney(): BigDecimal? =
        replace(",", "").trim().toBigDecimalOrNull()

    companion object {
        // KCB Pattern 1: P2P Outflow
        // Group 1: Reference (e.g. MBNHE344446SVLOD)
        // Group 2: Amount (e.g. 90.00)
        // Group 3: Source Account (e.g. 130****167)
        // Group 4: Recipient Phone (e.g. 254****445)
        // Group 5: Recipient Name (e.g. WILLYS OLUOCH ODUOR OTIENO)
        // Group 6: Date & Time (e.g. 2026-11-04 06:15:25 PM)
        // Group 7: Cost (optional)
        // Group 8: M-PESA REF (optional)
        val KCB_P2P_OUTFLOW_REGEX = Regex(
            pattern = """\b([A-Z0-9]{10,20})\s+KES\s*([0-9,]+(?:\.[0-9]{2})?)\s+sent\s+from\s+([0-9*X]+)\s+to\s+([0-9*X]+)\s*-\s*(.+?)\s+on\s+(\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}:\d{2}\s*[AP]M)(?:\.\s*Cost\s+KES\s*([0-9,.]+))?(?:.*?\bM-PESA\s+REF:\s*([A-Z0-9]+))?""",
            option = RegexOption.IGNORE_CASE
        )

        // KCB Pattern 2: Pay to M-PESA Biller
        // Group 1: Reference
        // Group 2: Amount
        // Group 3: Source Account
        // Group 4: Destination / Biller Account
        // Group 5: Date & Time
        // Group 6: Cost (optional)
        // Group 7: M-PESA REF (optional)
        val KCB_BILLER_OUTFLOW_REGEX = Regex(
            pattern = """\b([A-Z0-9]{10,20})\s+Completed\.\s*Your\s+Pay\s+to\s+M-PESA\s+Biller\s+request\s+of\s+KES\s*([0-9,]+(?:\.[0-9]{2})?)\s+from\s+([0-9*X]+)\s+to\s+(.+?)\s+at\s+(\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}:\d{2}\s*[AP]M)\s+has\s+been\s+processed\s+successfully(?:\.\s*Transaction\s+cost\s+KES\s*([0-9,.]+))?(?:.*?\bMPESA\s+REF:\s*([A-Z0-9]+))?""",
            option = RegexOption.IGNORE_CASE
        )

        // KCB Pattern 3: Modern Inflow
        val KCB_MODERN_INFLOW_REGEX = Regex(
            pattern = """\b([A-Z0-9]{10,20})(?:\s+Completed\.)?\s*KES\s*([0-9,]+(?:\.[0-9]{2})?)\s+received\s+(?:in|to)\s+([0-9*X]+)\s+from\s+(.+?)\s+on\s+(\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}:\d{2}\s*[AP]M)(?:.*?\bM-PESA\s+REF:\s*([A-Z0-9]+))?""",
            option = RegexOption.IGNORE_CASE
        )

        // NCBA Pattern 1: Received (Inflow)
        // "Dear JOY WANJIRU your transaction of KES 1,000.00 to JOY WANJIRU Acc 10******64 has been received with Mpesa Ref UJ2BW8L723 on 02/10/2026 10:31 PM. NCBA, Go for it."
        val NCBA_RECEIVED_REGEX = Regex(
            pattern = """Dear\s+(.+?)\s+your\s+transaction\s+of\s+KES\s*([0-9,]+(?:\.[0-9]{2})?)\s+to\s+(.+?)\s+Acc\s+([0-9*X]+)\s+has\s+been\s+received\s+with\s+Mpesa\s+Ref\s+([A-Z0-9]+)\s+on\s+(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s+\d{1,2}:\d{2}\s*[AP]M)""",
            option = RegexOption.IGNORE_CASE
        )

        // NCBA Pattern 2: Processed (Outflow)
        // "Dear JOY WANJIRU your transaction of KES 1,000.00 from JOY WANJIRU Acc 10******64 has been processed with Mpesa Ref UJ2BW8LBCF on 02/10/2026 10:48 PM. NCBA, Go for it."
        val NCBA_PROCESSED_REGEX = Regex(
            pattern = """Dear\s+(.+?)\s+your\s+transaction\s+of\s+KES\s*([0-9,]+(?:\.[0-9]{2})?)\s+from\s+(.+?)\s+Acc\s+([0-9*X]+)\s+has\s+been\s+processed\s+with\s+Mpesa\s+Ref\s+([A-Z0-9]+)\s+on\s+(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s+\d{1,2}:\d{2}\s*[AP]M)""",
            option = RegexOption.IGNORE_CASE
        )

        // Shared / Generic Extractor Regexes
        val AMOUNT_REGEX = Regex(
            pattern = """(?:KES|Ksh)\.?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)""",
            option = RegexOption.IGNORE_CASE
        )
        val AVAILABLE_BALANCE_REGEX = Regex(
            pattern = """(?:(?:New\s+)?Available\s+Balance\s+is|(?:New\s+)?Balance\s+is)\s*(?:KES|Ksh)\.?\s*([0-9][0-9,]*(?:\.[0-9]{2})?)""",
            option = RegexOption.IGNORE_CASE
        )
        val ACCOUNT_NUMBER_REGEX = Regex(
            pattern = """(?:A/C|AcC|Acc|Account)\s*[:#-]?\s*([0-9*X]{4,24})""",
            option = RegexOption.IGNORE_CASE
        )
        val REFERENCE_EXPLICIT_REGEX = Regex(
            pattern = """\b(?:Ref|Txn|Ref\s*No\.?)[:\s]+([A-Z0-9]{6,25})\b""",
            option = RegexOption.IGNORE_CASE
        )
        val MBN_REFERENCE_REGEX = Regex(
            pattern = """\b(MBN[A-Z0-9]{8,20})\b""",
            option = RegexOption.IGNORE_CASE
        )
        val MPESA_REF_REGEX = Regex(
            pattern = """\b(?:Mpesa\s+Ref|MPESA\s+REF|M-PESA\s+REF)[:\s]+([A-Z0-9]{8,12})\b""",
            option = RegexOption.IGNORE_CASE
        )
        val DATE_TIME_GENERIC_REGEX = Regex(
            pattern = """\b\d{1,2}[/-](?:\d{1,2}|[A-Za-z]{3})[/-]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?\s*[AP]?M?)?\b""",
            option = RegexOption.IGNORE_CASE
        )

        @RequiresApi(Build.VERSION_CODES.O)
        val DATE_FORMATTERS = listOf(
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd h:mm:ss a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd hh:mm:ss a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm:ss").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy hh:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy hh:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-M-yyyy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy h:mm a").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy HH:mm:ss").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy HH:mm:ss").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy").toFormatter(Locale.ENGLISH),
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy").toFormatter(Locale.ENGLISH),
        )
    }
}
