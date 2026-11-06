package com.example.smartmoney.core.sms.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A normalized representation of an M-PESA financial movement extracted from a message.
 *
 * This is a parsing result only. It does not prove the sender is trusted, prevent duplicates,
 * update balances, or persist anything to Room.
 */
data class ParsedFinancialMovement(
    val reference: String,
    val amount: BigDecimal,
    val direction: TransactionDirection,
    val kind: MpesaTransactionKind,
    val counterparty: String? = null,
    val counterpartyPhone: String? = null,
    val accountReference: String? = null,
    val balanceAfter: BigDecimal? = null,
    /** Product-specific secondary balance extracted from dual-balance messages (e.g. M-Shwari savings balance). */
    val secondaryBalanceAfter: BigDecimal? = null,
    val fee: BigDecimal? = null,
    val transactionDateTime: LocalDateTime? = null,
    val currency: String = "KES",
    /** Set for reversal messages when the original transaction reference is identifiable. */
    val reversedReference: String? = null,
)

enum class TransactionDirection {
    CREDIT,
    DEBIT,
}

enum class MpesaTransactionKind {
    SEND_MONEY,
    RECEIVE_MONEY,
    PAYBILL,
    BUY_GOODS,
    AGENT_WITHDRAWAL,
    ATM_WITHDRAWAL,
    FULIZA,
    BANK_TO_MPESA,
    MPESA_TO_BANK,
    REVERSAL,
    AIRTIME,
    MPESA_TO_MSHWARI,
    MSHWARI_TO_MPESA,
}