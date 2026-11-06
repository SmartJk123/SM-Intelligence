package com.example.smartmoney.core.sms.ingestion

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.core.sms.model.MpesaTransactionKind
import com.example.smartmoney.core.sms.model.ParsedFinancialMovement
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Service orchestrating the local, offline ingestion of validated M-PESA transaction movements
 * into the Room database.
 *
 * Responsibilities:
 * 1. Validates parsed movement invariants (positive amount, non-blank reference, valid directions).
 * 2. Resolves the appropriate local M-PESA account without inventing fake credentials or accounts.
 * 3. Enforces receipt reference deduplication safely using deterministic primary keys and atomic insert.
 * 4. Maps domain financial movements into [TransactionEntity] without losing counterparty or fee context.
 * 5. Strictly prevents automatic account balance mutations in accordance with Phase 2 constraints.
 */
class MpesaTransactionIngestionService(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val userIdProvider: (() -> String?)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {

    // Process-level mutex to serialize concurrent ingestion requests and prevent race conditions
    private val ingestionMutex = Mutex()

    /**
     * Ingests a [ParsedFinancialMovement] into the local Room database for [userId].
     *
     * @param movement The parsed M-PESA movement data.
     * @param userId The ID of the user owning the transaction. Defaults to [userIdProvider].
     * @param targetAccountId Optional specific M-PESA account ID to bind to.
     * @return [MpesaIngestionResult] representing the explicit outcome.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun ingest(
        movement: ParsedFinancialMovement,
        userId: String? = null,
        targetAccountId: String? = null
    ): MpesaIngestionResult = withContext(dispatchers.io) {
        // 1. Validate Receipt Reference
        val reference = movement.reference.trim().uppercase(Locale.ROOT)
        if (reference.isBlank()) {
            return@withContext MpesaIngestionResult.InvalidMovement("Receipt reference cannot be blank.")
        }
        if (!reference.matches(Regex("^[A-Z0-9]{5,20}$"))) {
            return@withContext MpesaIngestionResult.InvalidMovement(
                "Receipt reference '$reference' does not conform to valid M-PESA alphanumeric format."
            )
        }

        // 2. Validate Monetary Amount
        if (movement.amount <= BigDecimal.ZERO) {
            return@withContext MpesaIngestionResult.InvalidMovement(
                "Transaction amount must be strictly greater than zero. Received: ${movement.amount}"
            )
        }

        // 3. Resolve Target User ID
        val resolvedUserId = (userId ?: userIdProvider?.invoke())?.trim().orEmpty()
        if (resolvedUserId.isBlank()) {
            return@withContext MpesaIngestionResult.MissingAccount(
                "Cannot ingest transaction: No authenticated user ID is available."
            )
        }

        // 4. Resolve Local M-PESA Account
        val account = when (val resolution = resolveMpesaAccountSafely(resolvedUserId, targetAccountId)) {
            is AccountLookupOutcome.Found -> resolution.account
            is AccountLookupOutcome.Missing -> return@withContext MpesaIngestionResult.MissingAccount(resolution.reason)
            is AccountLookupOutcome.Ambiguous -> return@withContext MpesaIngestionResult.AmbiguousAccount(
                reason = resolution.reason,
                candidateAccountIds = resolution.candidateIds
            )
            is AccountLookupOutcome.Error -> return@withContext MpesaIngestionResult.DatabaseError(
                cause = resolution.cause,
                message = resolution.message
            )
        }

        // 5. Concurrency Protection & Duplicate Prevention
        val deterministicTransactionId = "mpesa_$reference"

        ingestionMutex.withLock {
            try {
                // Check if already imported
                val existing = transactionDao.findTransactionByProviderIdOrId(
                    providerTxId = reference,
                    id = deterministicTransactionId
                )
                when (val dupCheck = evaluateDuplicateStatus(existing, reference, deterministicTransactionId)) {
                    is DuplicateStatus.ConfirmedDuplicate -> {
                        return@withContext MpesaIngestionResult.Duplicate(
                            reference = reference,
                            existingTransactionId = dupCheck.existingTransactionId,
                            message = "Transaction $reference has already been imported into account '${dupCheck.accountId}'."
                        )
                    }
                    is DuplicateStatus.IdCollision -> {
                        return@withContext MpesaIngestionResult.DatabaseError(
                            cause = IllegalStateException("Deterministic transaction ID collision for '$deterministicTransactionId'."),
                            message = "ID collision detected: Record '${dupCheck.existingId}' already exists with provider reference '${dupCheck.existingProviderId}', which does not match '$reference'."
                        )
                    }
                    DuplicateStatus.NotDuplicate -> {
                        // Proceed to insert
                    }
                }

                // 6. Map Movement to TransactionEntity
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val timestampIso = movement.transactionDateTime?.let { dt ->
                    try {
                        dt.atZone(java.time.ZoneId.of("Africa/Nairobi")).toInstant().toString()
                    } catch (_: Exception) {
                        nowIso
                    }
                } ?: nowIso

                val structuredDescription = buildStructuredDescription(movement)

                val entity = TransactionEntity(
                    id = deterministicTransactionId,
                    userId = resolvedUserId,
                    accountId = account.id.ifBlank { account.accountId },
                    amount = movement.amount,
                    transactionType = movement.direction.name, // "CREDIT" or "DEBIT"
                    timestamp = timestampIso,
                    description = structuredDescription,
                    providerTransactionId = reference,
                    createdAt = nowIso,
                    updatedAt = nowIso
                )

                // 7. Atomic Insert into Room (ABORT on conflict)
                transactionDao.insertTransaction(entity)

                // 8. Safely update M-PESA account balance if balanceAfter is extracted from SMS
                if (movement.balanceAfter != null) {
                    try {
                        accountDao.updateAccountBalance(
                            userId = resolvedUserId,
                            accountId = account.id,
                            newBalance = movement.balanceAfter
                        )
                    } catch (_: Exception) {
                        // Tolerate balance update failure without aborting transaction success
                    }
                }

                // 9. Safely update secondary account balance (e.g. M-Shwari Savings) if extracted
                if (movement.secondaryBalanceAfter != null) {
                    try {
                        val mshwariAccount = accountDao.getAccountsForUserOnce(resolvedUserId).firstOrNull {
                            val inst = it.institution.uppercase(Locale.ROOT)
                            val name = it.accountName.uppercase(Locale.ROOT)
                            inst.contains("M-SHWARI") || inst.contains("MSHWARI") || name.contains("M-SHWARI") || name.contains("MSHWARI")
                        }
                        if (mshwariAccount != null) {
                            accountDao.updateAccountBalance(
                                userId = resolvedUserId,
                                accountId = mshwariAccount.id,
                                newBalance = movement.secondaryBalanceAfter
                            )
                        } else {
                            val mshwariId = "account_mshwari_$resolvedUserId"
                            val mshwariWallet = AccountEntity(
                                id = mshwariId,
                                userId = resolvedUserId,
                                accountId = "MSHWARI_${resolvedUserId.take(8).uppercase()}",
                                accountName = "M-Shwari Savings",
                                institution = "M-Shwari",
                                accountType = "INVESTMENT",
                                currency = "KES",
                                ledgerBalance = movement.secondaryBalanceAfter,
                                availableBalance = movement.secondaryBalanceAfter,
                                creditOutstanding = BigDecimal.ZERO,
                                creditLimit = BigDecimal.ZERO,
                                availableCredit = BigDecimal.ZERO,
                                accountStatus = "ACTIVE",
                                connectionStatus = "CONNECTED",
                                dataSource = "OFFLINE_SMS",
                                createdAt = nowIso,
                                updatedAt = nowIso
                            )
                            accountDao.upsertAccount(mshwariWallet)
                        }
                    } catch (_: Exception) {
                        // Tolerate secondary balance update failure without aborting transaction success
                    }
                }

                return@withContext MpesaIngestionResult.Success(
                    transaction = entity,
                    message = "Transaction $reference successfully imported."
                )

            } catch (e: SQLiteConstraintException) {
                // Check if race condition occurred and record was inserted by concurrent thread
                val concurrentExisting = transactionDao.findTransactionByProviderIdOrId(
                    providerTxId = reference,
                    id = deterministicTransactionId
                )
                when (val dupCheck = evaluateDuplicateStatus(concurrentExisting, reference, deterministicTransactionId)) {
                    is DuplicateStatus.ConfirmedDuplicate -> {
                        return@withContext MpesaIngestionResult.Duplicate(
                            reference = reference,
                            existingTransactionId = dupCheck.existingTransactionId,
                            message = "Concurrent ingestion detected: Transaction $reference already exists."
                        )
                    }
                    else -> {
                        return@withContext MpesaIngestionResult.DatabaseError(
                            cause = e,
                            message = "Database constraint violation during insertion: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return@withContext MpesaIngestionResult.DatabaseError(
                    cause = e,
                    message = "Database persistence failure: ${e.message}"
                )
            }
        }
    }

    private suspend fun resolveMpesaAccountSafely(
        userId: String,
        targetAccountId: String?
    ): AccountLookupOutcome {
        val allUserAccounts = try {
            accountDao.getAccountsForUserOnce(userId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return AccountLookupOutcome.Error(
                cause = e,
                message = "Failed to query user accounts for '$userId'."
            )
        }

        if (!targetAccountId.isNullOrBlank()) {
            val candidate = allUserAccounts.firstOrNull {
                it.id == targetAccountId || it.accountId == targetAccountId
            }
            return when {
                candidate == null -> AccountLookupOutcome.Missing(
                    "Specified account '$targetAccountId' was not found for user '$userId'."
                )
                !isMpesaAccount(candidate) -> AccountLookupOutcome.Missing(
                    "Specified account '$targetAccountId' belongs to institution '${candidate.institution}' and is not a valid M-PESA account."
                )
                else -> AccountLookupOutcome.Found(candidate)
            }
        }

        val eligibleAccounts = allUserAccounts.filter { isMpesaAccount(it) }
        return when {
            eligibleAccounts.isEmpty() -> {
                // Zero-Friction: Automatically provision a canonical M-PESA Wallet for this user
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())
                val autoWalletId = "account_mpesa_$userId"
                val autoWallet = AccountEntity(
                    id = autoWalletId,
                    userId = userId,
                    accountId = "MPESA_${userId.take(8).uppercase()}",
                    accountName = "M-PESA Wallet",
                    institution = "M-PESA",
                    accountType = "MOBILE_MONEY",
                    currency = "KES",
                    ledgerBalance = BigDecimal.ZERO,
                    availableBalance = BigDecimal.ZERO,
                    creditOutstanding = BigDecimal.ZERO,
                    creditLimit = BigDecimal.ZERO,
                    availableCredit = BigDecimal.ZERO,
                    accountStatus = "ACTIVE",
                    connectionStatus = "CONNECTED",
                    dataSource = "OFFLINE_SMS",
                    createdAt = nowIso,
                    updatedAt = nowIso
                )
                try {
                    accountDao.upsertAccount(autoWallet)
                    AccountLookupOutcome.Found(autoWallet)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AccountLookupOutcome.Error(
                        cause = e,
                        message = "Failed to auto-provision M-PESA Wallet for user '$userId': ${e.message}"
                    )
                }
            }
            eligibleAccounts.size == 1 -> AccountLookupOutcome.Found(eligibleAccounts.first())
            else -> {
                val candidateSummary = eligibleAccounts.map { "${it.accountName} (${it.id})" }
                AccountLookupOutcome.Ambiguous(
                    reason = "Multiple eligible M-PESA accounts found for user '$userId': $candidateSummary. Please specify targetAccountId to disambiguate.",
                    candidateIds = eligibleAccounts.map { it.id }
                )
            }
        }
    }

    private fun isMpesaAccount(account: AccountEntity): Boolean {
        val inst = account.institution.trim().uppercase(Locale.ROOT)
        val name = account.accountName.trim().uppercase(Locale.ROOT)

        // Ensure M-Shwari savings accounts are not mistakenly picked as the primary M-PESA wallet
        if (inst.contains("M-SHWARI") || inst.contains("MSHWARI") ||
            name.contains("M-SHWARI") || name.contains("MSHWARI")) {
            return false
        }

        // Definite M-PESA or Safaricom branding in institution or account title
        return inst.contains("M-PESA") || inst.contains("MPESA") || inst.contains("SAFARICOM") ||
                name.contains("M-PESA") || name.contains("MPESA") || name.contains("SAFARICOM")
    }

    private fun evaluateDuplicateStatus(
        existing: TransactionEntity?,
        reference: String,
        deterministicId: String
    ): DuplicateStatus {
        if (existing == null) return DuplicateStatus.NotDuplicate

        val existingProviderId = existing.providerTransactionId?.trim()?.uppercase(Locale.ROOT)

        // 1. Confirmed duplicate by matching provider receipt reference
        if (existingProviderId != null && existingProviderId == reference) {
            return DuplicateStatus.ConfirmedDuplicate(
                existingTransactionId = existing.id,
                accountId = existing.accountId
            )
        }

        // 2. Confirmed duplicate by deterministic ID when providerTransactionId is null or matches
        if (existing.id == deterministicId && (existingProviderId.isNullOrBlank() || existingProviderId == reference)) {
            return DuplicateStatus.ConfirmedDuplicate(
                existingTransactionId = existing.id,
                accountId = existing.accountId
            )
        }

        // 3. ID matches deterministic ID but providerTransactionId is different: ID collision with unrelated transaction
        return DuplicateStatus.IdCollision(
            existingId = existing.id,
            existingProviderId = existingProviderId.orEmpty()
        )
    }

    private sealed interface AccountLookupOutcome {
        data class Found(val account: AccountEntity) : AccountLookupOutcome
        data class Missing(val reason: String) : AccountLookupOutcome
        data class Ambiguous(val reason: String, val candidateIds: List<String>) : AccountLookupOutcome
        data class Error(val cause: Throwable, val message: String) : AccountLookupOutcome
    }

    private sealed interface DuplicateStatus {
        data object NotDuplicate : DuplicateStatus
        data class ConfirmedDuplicate(val existingTransactionId: String, val accountId: String) : DuplicateStatus
        data class IdCollision(val existingId: String, val existingProviderId: String) : DuplicateStatus
    }

    private fun buildStructuredDescription(movement: ParsedFinancialMovement): String {
        val baseDescription = when (movement.kind) {
            MpesaTransactionKind.REVERSAL -> {
                buildString {
                    append("M-PESA Reversal")
                    if (!movement.reversedReference.isNullOrBlank()) {
                        append(" of ").append(movement.reversedReference)
                    }
                }
            }
            MpesaTransactionKind.BUY_GOODS -> {
                "Buy Goods - ${movement.counterparty ?: "Merchant"}"
            }
            MpesaTransactionKind.PAYBILL -> {
                buildString {
                    append("Paybill - ").append(movement.counterparty ?: "Business")
                    if (!movement.accountReference.isNullOrBlank()) {
                        append(" (Acc: ").append(movement.accountReference).append(")")
                    }
                }
            }
            MpesaTransactionKind.AGENT_WITHDRAWAL -> {
                "Withdrawal - ${movement.counterparty ?: "Agent"}"
            }
            MpesaTransactionKind.ATM_WITHDRAWAL -> {
                "ATM Withdrawal - ${movement.counterparty ?: "ATM"}"
            }
            MpesaTransactionKind.AIRTIME -> {
                "Airtime Purchase"
            }
            MpesaTransactionKind.FULIZA -> {
                "Fuliza M-PESA"
            }


            MpesaTransactionKind.BANK_TO_MPESA -> {
                "Bank Transfer from ${movement.counterparty ?: "Bank"}"
            }
            MpesaTransactionKind.MPESA_TO_BANK -> {
                buildString {
                    append("Transfer to Bank - ").append(movement.counterparty ?: "Bank")
                    if (!movement.accountReference.isNullOrBlank()) {
                        append(" (Acc: ").append(movement.accountReference).append(")")
                    }
                }
            }
            MpesaTransactionKind.SEND_MONEY -> {
                buildString {
                    append("Sent to ").append(movement.counterparty ?: "Recipient")
                    if (!movement.counterpartyPhone.isNullOrBlank()) {
                        append(" ").append(movement.counterpartyPhone)
                    }
                }
            }
            MpesaTransactionKind.RECEIVE_MONEY -> {
                buildString {
                    append("Received from ").append(movement.counterparty ?: "Sender")
                    if (!movement.counterpartyPhone.isNullOrBlank()) {
                        append(" ").append(movement.counterpartyPhone)
                    }
                }
            }

            MpesaTransactionKind.MPESA_TO_MSHWARI -> {
                buildString {
                    append("Transfer to M-Shwari")
                    if (movement.secondaryBalanceAfter != null) {
                        append(" • M-Shwari Bal: KES ").append(movement.secondaryBalanceAfter.toPlainString())
                    }
                }
            }
            MpesaTransactionKind.MSHWARI_TO_MPESA -> {
                buildString {
                    append("Transfer from M-Shwari")
                    if (movement.secondaryBalanceAfter != null) {
                        append(" • M-Shwari Bal: KES ").append(movement.secondaryBalanceAfter.toPlainString())
                    }
                }
            }
        }

        return buildString {
            append(baseDescription)
            if (movement.fee != null && movement.fee > BigDecimal.ZERO) {
                append(" • Fee: KES ").append(movement.fee.toPlainString())
            }
        }
    }
}
