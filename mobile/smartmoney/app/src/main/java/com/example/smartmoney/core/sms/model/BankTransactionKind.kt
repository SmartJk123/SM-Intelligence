package com.example.smartmoney.core.sms.model

/**
 * High-level categorization of commercial bank transactions parsed from SMS notifications.
 */
enum class BankTransactionKind {
    TRANSFER_IN,
    TRANSFER_OUT,
    DEPOSIT,
    WITHDRAWAL,
    ATM_WITHDRAWAL,
    POS_PURCHASE,
    BANK_CHARGES,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    GENERAL_CREDIT,
    GENERAL_DEBIT
}

/**
 * Recognized commercial bank institutions.
 */
enum class BankInstitution(val displayName: String, val institutionCode: String) {
    KCB(displayName = "KCB Bank", institutionCode = "KCB"),
    NCBA(displayName = "NCBA Bank", institutionCode = "NCBA")
}
