package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.NotificationDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.NotificationEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.BankTransactionResponse
import com.example.smartmoney.data.remote.datasource.TransactionRemoteDataSource
import com.example.smartmoney.data.remote.dto.TransactionDto
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.content.Context
import com.example.smartmoney.core.notification.NotificationHelper
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

class TransactionRepositoryImpl(
    private val remoteDataSource: TransactionRemoteDataSource,
    private val localDao: TransactionDao,
    private val accountDao: AccountDao? = null,
    private val notificationDao: NotificationDao? = null,
    private val userIdProvider: (() -> String?)? = null,
    private val context: Context? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : TransactionRepository {

    private val alertedTxIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var isFirstSync = true
    private var isFirstRemoteSync = true
    private var isFirstActivitySync = true

    override fun getTransactionsFlow(userId: String?, accountId: String?): Flow<List<Transaction>> {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (targetUserId.isNullOrBlank()) {
            return flowOf(emptyList())
        }
        val flow = if (accountId != null) {
            localDao.getTransactionsForAccount(userId = targetUserId, accountId = accountId)
        } else {
            localDao.getTransactionsForUser(userId = targetUserId)
        }
        return flow.map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.default)
    }

    override suspend fun syncTransactions(userId: String?, accountId: String?): Result<Unit> = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (targetUserId.isNullOrBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            // 0. The signed-in user's real transactions (every linked bank, including live
            //    NCBA webhooks) from transactions-service, the same feed the web dashboard
            //    uses. The login token scopes it to this user, so it does not depend on the
            //    local account cache. Cash flow and the transaction list are built from this.
            syncActivityFeed(targetUserId, accountId)

            // Load verified accounts owned by targetUserId (checking both raw and UUID-hashed ID forms for isolation)
            val rawAccounts = accountDao?.getAccountsForUserOnce(targetUserId).orEmpty()
            val hashedUserId = try {
                UUID.nameUUIDFromBytes(targetUserId.toByteArray()).toString()
            } catch (_: Exception) { null }
            val hashedAccounts = if (hashedUserId != null && hashedUserId != targetUserId) {
                accountDao?.getAccountsForUserOnce(hashedUserId).orEmpty()
            } else emptyList()
            val userAccounts = (rawAccounts + hashedAccounts).filterNot { it.id.endsWith("_alias") }.distinctBy { it.accountId }

            if (userAccounts.isEmpty()) {
                // User has no accounts linked: do not ingest or attribute external movements
                return@withContext Result.success(Unit)
            }

            // 1. Ingest bank transactions from bank-integration-service (:8090)
            try {
                val targetAccountNum = if (accountId != null) {
                    userAccounts.firstOrNull { it.id == accountId || it.accountId == accountId }?.accountId
                } else null
                val kcbResponse = RetrofitClient.bankIntegrationApi.getKcbTransactions(
                    userId = targetUserId,
                    accountNumber = targetAccountNum
                )
                if (kcbResponse.isSuccessful) {
                    val kcbList = kcbResponse.body().orEmpty()

                    // STRICT FILTER: Match incoming transaction against accounts actually owned by targetUserId
                    val verifiedMovements: List<Pair<BankTransactionResponse, AccountEntity>> = kcbList.mapNotNull { kcbTx: BankTransactionResponse ->
                        val txAccountNum = kcbTx.accountNumber?.trim().orEmpty()
                        val bankKey = (kcbTx.bankId.ifBlank { "kcb" }).trim().lowercase(Locale.ROOT)

                        val matchedAccount = userAccounts.firstOrNull { acc ->
                            val accNum = acc.accountId.trim()
                            val accLast4 = accNum.takeLast(4)
                            val matchesNum = txAccountNum.isNotBlank() && (
                                accNum.equals(txAccountNum, ignoreCase = true) ||
                                (accLast4.isNotBlank() && txAccountNum.endsWith(accLast4))
                            )
                            val matchesBank = acc.institution.trim().equals(bankKey, ignoreCase = true) ||
                                              acc.accountName.contains(bankKey, ignoreCase = true)
                            if (txAccountNum.isNotBlank()) {
                                matchesNum && matchesBank
                            } else {
                                matchesBank
                            }
                        }

                        // If sync was requested for a specific account, only accept if it matches
                        if (accountId != null && matchedAccount?.id != accountId && matchedAccount?.accountId != accountId) {
                            null
                        } else if (matchedAccount != null) {
                            kcbTx to matchedAccount
                        } else {
                            // Drop movement: has no relation to any account owned by targetUserId
                            null
                        }
                    }

                    val kcbEntities = withContext(dispatchers.default) {
                        verifiedMovements.map { (kcbTx, matchedAcc) ->
                            val amountParsed = try {
                                BigDecimal(kcbTx.amount)
                            } catch (_: Exception) {
                                BigDecimal.ZERO
                            }
                            val nowIso = java.time.Instant.now().toString()
                            val bankKey = (kcbTx.bankId.ifBlank { "kcb" }).lowercase(Locale.ROOT)
                            val bankUpper = bankKey.uppercase(Locale.ROOT)
                            val txId = kcbTx.reference.ifBlank { kcbTx.id?.toString() ?: nowIso }
                            TransactionEntity(
                                id = "${bankKey}_$txId",
                                userId = targetUserId,
                                accountId = matchedAcc.id,
                                amount = amountParsed,
                                transactionType = kcbTx.direction.uppercase(),
                                timestamp = kcbTx.bookingDate ?: kcbTx.createdAt ?: nowIso,
                                description = kcbTx.narration ?: "$bankUpper ${kcbTx.direction} - ${kcbTx.reference}",
                                providerTransactionId = kcbTx.reference,
                                createdAt = kcbTx.createdAt ?: nowIso,
                                updatedAt = kcbTx.createdAt ?: nowIso
                            )
                        }
                    }
                    if (kcbEntities.isNotEmpty()) {
                        localDao.upsertTransactions(kcbEntities)
                    }

                    // Ingest notifications for verified movements (persisted in Room, duplicate-safe)
                    if (notificationDao != null && verifiedMovements.isNotEmpty()) {
                        val notifEntities = verifiedMovements.map { (tx, matchedAcc) ->
                            val isCredit = !tx.direction.equals("Debit", ignoreCase = true)
                            val amt = try { BigDecimal(tx.amount) } catch (_: Exception) { BigDecimal.ZERO }
                            val formatted = DecimalFormat("#,##0.00").format(amt)
                            val bankUpper = (tx.bankId.ifBlank { "kcb" }).uppercase(Locale.ROOT)
                            val title = if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid"
                            val refStr = if (!tx.reference.isNullOrBlank()) " (Ref: ${tx.reference})" else ""
                            val noteStr = if (!tx.narration.isNullOrBlank()) " • ${tx.narration}" else ""
                            val accNameStr = if (!matchedAcc.accountName.isNullOrBlank()) " (${matchedAcc.accountName})" else ""
                            val message = if (isCredit) {
                                "KES $formatted credited to your $bankUpper account$accNameStr$noteStr$refStr"
                            } else {
                                "KES $formatted debited from your $bankUpper account$accNameStr$noteStr$refStr"
                            }
                            val timestampStr = tx.bookingDate ?: tx.createdAt ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                            NotificationEntity(
                                id = "notif_${tx.reference.ifBlank { tx.id?.toString() ?: timestampStr }}",
                                userId = targetUserId,
                                title = title,
                                message = message,
                                amount = amt,
                                type = if (isCredit) "CREDIT" else "DEBIT",
                                timestamp = timestampStr,
                                reference = tx.reference,
                                isRead = false
                            )
                        }
                        notificationDao.insertNotifications(notifEntities)
                    }

                    // 1b. Post Android heads-up system notification for brand-new transactions
                    if (context != null && verifiedMovements.isNotEmpty()) {
                        if (isFirstSync) {
                            verifiedMovements.forEach { (tx, _) ->
                                val key = tx.reference.ifBlank { tx.id?.toString() ?: "" }
                                if (key.isNotBlank()) alertedTxIds.add(key)
                            }
                            isFirstSync = false
                        } else {
                            for ((tx, _) in verifiedMovements) {
                                val key = tx.reference.ifBlank { tx.id?.toString() ?: "" }
                                if (key.isNotBlank() && alertedTxIds.add(key)) {
                                    val isCredit = !tx.direction.equals("Debit", ignoreCase = true)
                                    val amt = try { BigDecimal(tx.amount) } catch (_: Exception) { BigDecimal.ZERO }
                                    val formatted = DecimalFormat("#,##0.00").format(amt)
                                    val bankUpper = (tx.bankId.ifBlank { "kcb" }).uppercase(Locale.ROOT)
                                    val title = if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid"
                                    val note = tx.narration ?: if (isCredit) "Funds received" else "Payment sent"
                                    val refStr = if (!tx.reference.isNullOrBlank()) " (${tx.reference})" else ""
                                    val msg = if (isCredit) {
                                        "+KES $formatted credited • $note$refStr"
                                    } else {
                                        "-KES $formatted debited • $note$refStr"
                                    }
                                    NotificationHelper.postSystemNotification(context, title, msg)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Gracefully tolerate when bank-integration-service is unreachable
            }

            // 2. Fetch standard remote transactions strictly scoped to user's accounts
            try {
                val targetAccountIds = if (accountId != null) {
                    userAccounts.filter { it.id == accountId || it.accountId == accountId }.map { it.id }
                } else {
                    userAccounts.map { it.id }
                }

                if (targetAccountIds.isNotEmpty()) {
                    val remoteDtos = remoteDataSource.fetchTransactions(targetAccountIds)
                    val domainTransactions = withContext(dispatchers.default) {
                        remoteDtos.map { it.toDomain() }
                    }
                    val entities = domainTransactions
                        .filter { it.accountId in targetAccountIds }
                        .map { TransactionEntity.fromDomain(it, userId = targetUserId) }
                    if (entities.isNotEmpty()) {
                        localDao.upsertTransactions(entities)

                        // Ingest in-app notifications for live remote movements (e.g. NCBA live webhooks)
                        if (notificationDao != null) {
                            val notifEntities = entities.map { entity ->
                                val isCredit = !entity.transactionType.equals("DEBIT", ignoreCase = true)
                                val matchedAcc = userAccounts.firstOrNull { it.id == entity.accountId || it.accountId == entity.accountId }
                                val bankUpper = (matchedAcc?.institution?.takeIf { it.isNotBlank() } ?: "NCBA").uppercase(Locale.ROOT)
                                val title = if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid"
                                val amt = entity.amount
                                val formatted = DecimalFormat("#,##0.00").format(amt)
                                val noteStr = if (!entity.description.isNullOrBlank()) " • ${entity.description}" else ""
                                val accNameStr = if (!matchedAcc?.accountName.isNullOrBlank()) " (${matchedAcc?.accountName})" else ""
                                val refPart = if (!entity.providerTransactionId.isNullOrBlank()) " (Ref: ${entity.providerTransactionId})" else ""
                                val message = if (isCredit) {
                                    "KES $formatted credited to your $bankUpper account$accNameStr$noteStr$refPart"
                                } else {
                                    "KES $formatted debited from your $bankUpper account$accNameStr$noteStr$refPart"
                                }
                                val notifKey = entity.providerTransactionId?.ifBlank { null } ?: entity.id
                                NotificationEntity(
                                    id = "notif_${notifKey}",
                                    userId = targetUserId,
                                    title = title,
                                    message = message,
                                    amount = amt,
                                    type = if (isCredit) "CREDIT" else "DEBIT",
                                    timestamp = entity.timestamp,
                                    reference = entity.providerTransactionId,
                                    isRead = false
                                )
                            }
                            notificationDao.insertNotifications(notifEntities)
                        }

                        // Post Android heads-up system notification for brand-new live transactions
                        if (context != null) {
                            if (isFirstRemoteSync) {
                                entities.forEach { entity ->
                                    val key = entity.providerTransactionId?.ifBlank { null } ?: entity.id
                                    if (key.isNotBlank()) alertedTxIds.add(key)
                                }
                                isFirstRemoteSync = false
                            } else {
                                for (entity in entities) {
                                    val key = entity.providerTransactionId?.ifBlank { null } ?: entity.id
                                    if (key.isNotBlank() && alertedTxIds.add(key)) {
                                        val isCredit = !entity.transactionType.equals("DEBIT", ignoreCase = true)
                                        val matchedAcc = userAccounts.firstOrNull { it.id == entity.accountId || it.accountId == entity.accountId }
                                        val bankUpper = (matchedAcc?.institution?.takeIf { it.isNotBlank() } ?: "NCBA").uppercase(Locale.ROOT)
                                        val title = if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid"
                                        val amt = entity.amount
                                        val formatted = DecimalFormat("#,##0.00").format(amt)
                                        val note = entity.description ?: if (isCredit) "Funds received" else "Payment sent"
                                        val refStr = if (!entity.providerTransactionId.isNullOrBlank()) " (${entity.providerTransactionId})" else ""
                                        val msg = if (isCredit) {
                                            "+KES $formatted credited • $note$refStr"
                                        } else {
                                            "-KES $formatted debited • $note$refStr"
                                        }
                                        NotificationHelper.postSystemNotification(context, title, msg)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Tolerate if remote datasource is offline
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pulls GET /api/transactions/activity (token-scoped to the signed-in user), saves it into
     * Room (CREDIT = money in, DEBIT = money out) and raises notifications for new movements.
     * Never throws: offline or an unreachable gateway keeps what is cached.
     */
    private suspend fun syncActivityFeed(targetUserId: String, accountId: String?) {
        try {
            val response = RetrofitClient.activityApi.getActivity()
            if (!response.isSuccessful) return
            val items = response.body().orEmpty()
                .filter { accountId == null || it.accountId == accountId }
            if (items.isEmpty()) return

            val entities = withContext(dispatchers.default) {
                items.map { item ->
                    val booked = item.transactionDate ?: item.receivedAt ?: java.time.Instant.now().toString()
                    TransactionEntity(
                        id = "activity_${item.id}",
                        userId = targetUserId,
                        accountId = item.accountId,
                        amount = item.amount,
                        transactionType = item.direction.uppercase(Locale.ROOT),
                        timestamp = booked,
                        description = item.description?.takeIf { it.isNotBlank() }
                            ?: item.counterparty
                            ?: "${item.bank ?: "Bank"} ${item.direction.lowercase(Locale.ROOT)}",
                        providerTransactionId = item.id,
                        createdAt = item.receivedAt ?: booked,
                        updatedAt = item.receivedAt ?: booked
                    )
                }
            }
            localDao.upsertTransactions(entities)

            // In-app notifications (bell). IGNORE on conflict, so repeats are never duplicated.
            if (notificationDao != null) {
                val notifEntities = items.map { item ->
                    val isCredit = !item.direction.equals("DEBIT", ignoreCase = true)
                    val bankUpper = (item.bank?.takeIf { it.isNotBlank() } ?: "Bank").uppercase(Locale.ROOT)
                    val formatted = DecimalFormat("#,##0.00").format(item.amount)
                    val accNameStr = if (!item.accountName.isNullOrBlank()) " (${item.accountName})" else ""
                    val note = item.description?.takeIf { it.isNotBlank() } ?: item.counterparty
                    val noteStr = if (!note.isNullOrBlank()) " • $note" else ""
                    NotificationEntity(
                        id = "notif_activity_${item.id}",
                        userId = targetUserId,
                        title = if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid",
                        message = if (isCredit) {
                            "${item.currency ?: "KES"} $formatted credited to your $bankUpper account$accNameStr$noteStr"
                        } else {
                            "${item.currency ?: "KES"} $formatted debited from your $bankUpper account$accNameStr$noteStr"
                        },
                        amount = item.amount,
                        type = if (isCredit) "CREDIT" else "DEBIT",
                        timestamp = item.receivedAt ?: item.transactionDate ?: java.time.Instant.now().toString(),
                        reference = item.id,
                        isRead = false
                    )
                }
                notificationDao.insertNotifications(notifEntities)
            }

            // Heads-up banners only for movements that arrive after the first sync of this session,
            // so opening the app does not replay the whole history.
            if (context != null) {
                if (isFirstActivitySync) {
                    items.forEach { alertedTxIds.add("activity_${it.id}") }
                    isFirstActivitySync = false
                } else {
                    for (item in items) {
                        if (!alertedTxIds.add("activity_${item.id}")) continue
                        val isCredit = !item.direction.equals("DEBIT", ignoreCase = true)
                        val bankUpper = (item.bank?.takeIf { it.isNotBlank() } ?: "Bank").uppercase(Locale.ROOT)
                        val formatted = DecimalFormat("#,##0.00").format(item.amount)
                        val currency = item.currency ?: "KES"
                        val note = item.description?.takeIf { it.isNotBlank() } ?: item.counterparty
                            ?: if (isCredit) "Funds received" else "Payment sent"
                        NotificationHelper.postSystemNotification(
                            context,
                            if (isCredit) "$bankUpper Inflow Received" else "$bankUpper Outflow Paid",
                            if (isCredit) "+$currency $formatted credited • $note" else "-$currency $formatted debited • $note"
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Offline or the gateway is unreachable: keep what is cached.
        }
    }

    override suspend fun recordTransaction(transaction: Transaction, userId: String?): Result<Transaction> = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke() ?: ""
        try {
            val remoteDto = remoteDataSource.recordTransaction(TransactionDto.fromDomain(transaction))
            val domainTx = remoteDto.toDomain()
            localDao.upsertTransaction(TransactionEntity.fromDomain(domainTx, userId = targetUserId))
            Result.success(domainTx)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
