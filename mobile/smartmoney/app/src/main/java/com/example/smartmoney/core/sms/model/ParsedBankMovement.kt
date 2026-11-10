package com.example.smartmoney.core.sms.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Normalized domain representation of a financial movement extracted from a commercial bank SMS (KCB or NCBA).
 */
data class ParsedBankMovement(
    val reference: String,
    val institution: BankInstitution,
    val accountIdentifier: String,
    val maskedSuffix: String? = null,
    val amount: BigDecimal,
    val direction: TransactionDirection,
    val kind: BankTransactionKind,
    val balanceAfter: BigDecimal? = null,
    val fee: BigDecimal? = null,
    val narration: String? = null,
    val counterparty: String? = null,
    val counterpartyPhone: String? = null,
    val transactionDateTime: LocalDateTime? = null,
    val currency: String = "KES",
    val bridgeMpesaReference: String? = null
)
