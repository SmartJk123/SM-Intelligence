package com.example.smartmoney.core.sms.ingestion

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.core.sms.model.BankInstitution
import com.example.smartmoney.core.sms.model.ParsedBankMovement
import com.example.smartmoney.core.sms.model.TransactionDirection
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Service orchestrating the local, offline ingestion of validated Bank transaction movements
 * (KCB and NCBA) into Room SQLite.
 */
class BankTransactionIngestionService(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val userIdProvider: (() -> String?)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {

    private val ingestionMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun ingest(
        movement: ParsedBankMovement,
        userId: String? = null,
        targetAccountId: String? = null
    ): BankIngestionResult = withContext(dispatchers.io) {
        // 1. Invariant Validations
        val reference = movement.reference.trim().uppercase(Locale.ROOT)
        if (reference.isBlank()) {
            return@withContext BankIngestionResult.InvalidMovement("Bank reference cannot be blank.")
        }
        if (movement.amount <= BigDecimal.ZERO) {
            return@withContext BankIngestionResult.InvalidMovement(
                "Transaction amount must be strictly positive. Received: ${movement.amount}"
            )
        }

        // 2. User Authentication Check
        val resolvedUserId = (userId ?: userIdProvider?.invoke())?.trim().orEmpty()
        if (resolvedUserId.isBlank()) {
            return@withContext BankIngestionResult.MissingAccount(
                "Cannot ingest transaction: No authenticated user ID is available."
            )
        }

        // 3. Resolve Target Bank Account
        val account = when (val resolution = resolveBankAccountSafely(movement, resolvedUserId, targetAccountId)) {
            is AccountResolution.Found -> resolution.account
            is AccountResolution.Missing -> return@withContext BankIngestionResult.MissingAccount(resolution.reason)
            is AccountResolution.Ambiguous -> return@withContext BankIngestionResult.AmbiguousAccount(
                reason = resolution.reason,
                candidateAccountIds = resolution.candidateIds
            )
            is AccountResolution.Error -> return@withContext BankIngestionResult.DatabaseError(
                cause = resolution.cause,
                message = resolution.message
            )
        }

        // 4. Concurrency Protection & Deterministic Deduplication
        val bankPrefix = movement.institution.name.lowercase(Locale.ROOT)
        val deterministicId = "${bankPrefix}_$reference"

        ingestionMutex.withLock {
            try {
                val existing = transactionDao.findTransactionByProviderIdOrId(
                    providerTxId = reference,
                    id = deterministicId
                )

                if (existing != null) {
                    val existingProviderId = existing.providerTransactionId?.trim()?.uppercase(Locale.ROOT)
                    if (existingProviderId == reference || existing.id == deterministicId) {
                        return@withContext BankIngestionResult.Duplicate(
                            reference = reference,
                            existingTransactionId = existing.id,
                            message = "Bank transaction $reference has already been recorded in account '${account.accountName}'."
                        )
                    }
                }

                // 5. Build TransactionEntity
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date())

                val timestampIso = movement.transactionDateTime?.let { dt ->
                    try {
                        dt.atZone(ZoneId.of("Africa/Nairobi")).toInstant().toString()
                    } catch (_: Exception) {
                        nowIso
                    }
                } ?: nowIso

                val structuredDescription = buildStructuredDescription(movement)

                val entity = TransactionEntity(
                    id = deterministicId,
                    userId = resolvedUserId,
                    accountId = account.id.ifBlank { account.accountId },
                    amount = movement.amount,
                    transactionType = movement.direction.name, // "CREDIT" or "DEBIT"
                    timestamp = timestampIso,
                    description = structuredDescription,
                    providerTransactionId = reference,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    source = "LOCAL_SMS",
                    syncStatus = "LOCAL_ONLY"
                )

                // 6. Atomic Insert (Abort on conflict)
                transactionDao.insertTransaction(entity)

                // 7. Balance Synchronization
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
                } else {
                    // Running balance not in SMS: adjust relative balance
                    try {
                        val delta = if (movement.direction == TransactionDirection.CREDIT) {
                            movement.amount
                        } else {
                            movement.amount.negate()
                        }
                        accountDao.adjustAccountBalance(
                            userId = resolvedUserId,
                            accountId = account.id,
                            delta = delta
                        )
                    } catch (_: Exception) {
                        // Tolerate balance update failure
                    }
                }

                return@withContext BankIngestionResult.Success(
                    transaction = entity,
                    message = "${movement.institution.displayName} transaction $reference successfully recorded."
                )

            } catch (e: SQLiteConstraintException) {
                // Concurrent insert detected
                return@withContext BankIngestionResult.Duplicate(
                    reference = reference,
                    existingTransactionId = deterministicId,
                    message = "Concurrent ingestion: Transaction $reference already recorded."
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return@withContext BankIngestionResult.DatabaseError(
                    cause = e,
                    message = "Database persistence failure: ${e.message}"
                )
            }
        }
    }

    private suspend fun resolveBankAccountSafely(
        movement: ParsedBankMovement,
        userId: String,
        targetAccountId: String?
    ): AccountResolution {
        val allUserAccounts = try {
            accountDao.getAccountsForUserOnce(userId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return AccountResolution.Error(
                cause = e,
                message = "Failed to query user accounts for '$userId'."
            )
        }

        val targetBankCode = movement.institution.institutionCode.uppercase(Locale.ROOT)

        // 1. If explicit targetAccountId is provided, enforce it
        if (!targetAccountId.isNullOrBlank()) {
            val candidate = allUserAccounts.firstOrNull { it.id == targetAccountId || it.accountId == targetAccountId }
            return when {
                candidate == null -> AccountResolution.Missing(
                    "Specified account '$targetAccountId' was not found for user '$userId'."
                )
                !candidate.institution.equals(targetBankCode, ignoreCase = true) -> AccountResolution.Missing(
                    "Specified account '$targetAccountId' belongs to '${candidate.institution}' and not '$targetBankCode'."
                )
                else -> AccountResolution.Found(candidate)
            }
        }

        // 2. Filter accounts for this bank institution
        val bankAccounts = allUserAccounts.filter { it.institution.equals(targetBankCode, ignoreCase = true) }

        // Match by exact account identifier or suffix
        val parsedAcc = movement.accountIdentifier.trim()
        val suffix = movement.maskedSuffix?.trim()

        if (bankAccounts.isNotEmpty()) {
            // Check exact account identifier
            val exactMatch = bankAccounts.firstOrNull {
                it.accountId.equals(parsedAcc, ignoreCase = true) || it.id.equals(parsedAcc, ignoreCase = true)
            }
            if (exactMatch != null) {
                return AccountResolution.Found(exactMatch)
            }

            // Check suffix match
            if (!suffix.isNullOrBlank()) {
                val suffixMatches = bankAccounts.filter {
                    it.accountId.endsWith(suffix) ||
                            it.maskedIdentifier?.contains(suffix) == true ||
                            it.accountName.contains(suffix)
                }
                if (suffixMatches.size == 1) {
                    return AccountResolution.Found(suffixMatches.first())
                }
            }

            // If user only has exactly 1 account for this bank, bind to it
            if (bankAccounts.size == 1) {
                return AccountResolution.Found(bankAccounts.first())
            }

            // Ambiguity: multiple accounts exist and none cleanly matched
            val candidateSummary = bankAccounts.map { "${it.accountName} (${it.accountId})" }
            return AccountResolution.Ambiguous(
                reason = "Multiple ${movement.institution.displayName} accounts found: $candidateSummary. Suffix '$suffix' could not disambiguate.",
                candidateIds = bankAccounts.map { it.id }
            )
        }

        // 3. Zero-Friction Auto-Provisioning: User has no account for this bank yet
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

        val bankKey = movement.institution.name.lowercase(Locale.ROOT)
        val autoAccountId = "account_${bankKey}_$userId"
        val initialBal = movement.balanceAfter ?: BigDecimal.ZERO

        val autoAccount = AccountEntity(
            id = autoAccountId,
            userId = userId,
            accountId = movement.accountIdentifier,
            accountName = "${movement.institution.displayName} Account",
            institution = movement.institution.institutionCode,
            accountType = "BANK",
            currency = movement.currency,
            ledgerBalance = initialBal,
            availableBalance = initialBal,
            creditOutstanding = BigDecimal.ZERO,
            creditLimit = BigDecimal.ZERO,
            availableCredit = BigDecimal.ZERO,
            accountStatus = "ACTIVE",
            connectionStatus = "CONNECTED",
            maskedIdentifier = movement.accountIdentifier,
            dataSource = "OFFLINE_SMS",
            createdAt = nowIso,
            updatedAt = nowIso
        )

        return try {
            accountDao.upsertAccount(autoAccount)
            AccountResolution.Found(autoAccount)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AccountResolution.Error(
                cause = e,
                message = "Failed to auto-provision ${movement.institution.displayName} account: ${e.message}"
            )
        }
    }

    private fun buildStructuredDescription(movement: ParsedBankMovement): String {
        val base = when {
            !movement.narration.isNullOrBlank() -> movement.narration
            movement.direction == TransactionDirection.CREDIT -> "Funds received in ${movement.institution.displayName}"
            else -> "Payment from ${movement.institution.displayName}"
        }

        return buildString {
            append(base)
            if (!movement.bridgeMpesaReference.isNullOrBlank()) {
                append(" • M-PESA Ref: ").append(movement.bridgeMpesaReference)
            }
            if (movement.fee != null && movement.fee > BigDecimal.ZERO) {
                append(" • Fee: KES ").append(movement.fee.toPlainString())
            }
        }
    }

    private sealed interface AccountResolution {
        data class Found(val account: AccountEntity) : AccountResolution
        data class Missing(val reason: String) : AccountResolution
        data class Ambiguous(val reason: String, val candidateIds: List<String>) : AccountResolution
        data class Error(val cause: Throwable, val message: String) : AccountResolution
    }
}
