package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.NotificationDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.entity.NotificationEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.datasource.TransactionRemoteDataSource
import com.example.smartmoney.data.remote.dto.TransactionDto
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
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

    override fun getTransactionsFlow(userId: String?, accountId: String?): Flow<List<Transaction>> {
        val targetUserId = userId ?: userIdProvider?.invoke()
        val flow = when {
            !targetUserId.isNullOrBlank() && accountId != null ->
                localDao.getTransactionsForAccount(userId = targetUserId, accountId = accountId)
            !targetUserId.isNullOrBlank() ->
                localDao.getTransactionsForUser(userId = targetUserId)
            accountId != null ->
                localDao.getTransactionsForAccount(accountId)
            else ->
                localDao.getAllTransactions()
        }
        return flow.map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.default)
    }

    override suspend fun syncTransactions(userId: String?, accountId: String?): Result<Unit> = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke() ?: "default-user"
        try {
            // 1. Ingest KCB transactions from bank-integration-service (:8090)
            try {
                val kcbResponse = RetrofitClient.bankIntegrationApi.getKcbTransactions(userId = targetUserId)
                if (kcbResponse.isSuccessful) {
                    val kcbList = kcbResponse.body().orEmpty()
                    val kcbEntities = withContext(dispatchers.default) {
                        kcbList.map { kcbTx ->
                            val amountParsed = try {
                                BigDecimal(kcbTx.amount)
                            } catch (_: Exception) {
                                BigDecimal.ZERO
                            }
                            val nowIso = java.time.Instant.now().toString()
                            val txId = kcbTx.reference.ifBlank { kcbTx.id?.toString() ?: nowIso }
                            TransactionEntity(
                                id = "kcb_$txId",
                                userId = targetUserId,
                                accountId = accountId ?: "kcb",
                                amount = amountParsed,
                                transactionType = kcbTx.direction.uppercase(),
                                timestamp = kcbTx.bookingDate ?: kcbTx.createdAt ?: nowIso,
                                description = kcbTx.narration ?: "KCB ${kcbTx.direction} - ${kcbTx.reference}",
                                providerTransactionId = kcbTx.reference,
                                createdAt = kcbTx.createdAt ?: nowIso,
                                updatedAt = kcbTx.createdAt ?: nowIso
                            )
                        }
                    }
                    if (kcbEntities.isNotEmpty()) {
                        localDao.upsertTransactions(kcbEntities)
                    }

                    // Ingest notifications for any KCB transactions (persisted in Room, duplicate-safe)
                    if (notificationDao != null && kcbList.isNotEmpty()) {
                        val notifEntities = kcbList.map { tx ->
                            val isCredit = !tx.direction.equals("Debit", ignoreCase = true)
                            val amt = try { BigDecimal(tx.amount) } catch (_: Exception) { BigDecimal.ZERO }
                            val formatted = DecimalFormat("#,##0.00").format(amt)
                            val title = if (isCredit) "KCB Inflow Received" else "KCB Outflow Paid"
                            val refStr = if (!tx.reference.isNullOrBlank()) " (Ref: ${tx.reference})" else ""
                            val noteStr = if (!tx.narration.isNullOrBlank()) " • ${tx.narration}" else ""
                            val message = if (isCredit) {
                                "KES $formatted credited to your KCB account$noteStr$refStr"
                            } else {
                                "KES $formatted debited from your KCB account$noteStr$refStr"
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
                    if (context != null && kcbList.isNotEmpty()) {
                        if (isFirstSync) {
                            kcbList.forEach { tx ->
                                val key = tx.reference.ifBlank { tx.id?.toString() ?: "" }
                                if (key.isNotBlank()) alertedTxIds.add(key)
                            }
                            isFirstSync = false
                        } else {
                            for (tx in kcbList) {
                                val key = tx.reference.ifBlank { tx.id?.toString() ?: "" }
                                if (key.isNotBlank() && alertedTxIds.add(key)) {
                                    val isCredit = !tx.direction.equals("Debit", ignoreCase = true)
                                    val amt = try { BigDecimal(tx.amount) } catch (_: Exception) { BigDecimal.ZERO }
                                    val formatted = DecimalFormat("#,##0.00").format(amt)
                                    val title = if (isCredit) "KCB Inflow Received" else "KCB Outflow Paid"
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

            // 2. Fetch standard remote transactions
            try {
                val remoteDtos = remoteDataSource.fetchTransactions(accountId)
                val domainTransactions = withContext(dispatchers.default) {
                    remoteDtos.map { it.toDomain() }
                }
                val entities = domainTransactions.map { TransactionEntity.fromDomain(it, userId = targetUserId) }
                if (entities.isNotEmpty()) {
                    localDao.upsertTransactions(entities)
                }
            } catch (_: Exception) {
                // Tolerate if remote datasource is offline
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
