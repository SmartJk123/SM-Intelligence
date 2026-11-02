package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.TransactionApi
import com.example.smartmoney.data.remote.dto.CreateTransactionBackendRequest
import com.example.smartmoney.data.remote.dto.TransactionDto

/**
 * Remote data source routing transactions directly through the Render Gateway
 * to the Spring Boot transactions-service via Retrofit.
 * Completely eliminates any client-side Supabase PostgREST calls.
 */
class TransactionRemoteDataSource(
    private val transactionApi: TransactionApi = RetrofitClient.transactionApi
) {

    /**
     * Fetches transactions strictly bound to the specified [accountIds] via GET /api/transactions.
     */
    suspend fun fetchTransactions(accountIds: List<String>): List<TransactionDto> {
        val validIds = accountIds.filter { it.isNotBlank() }
        if (validIds.isEmpty()) return emptyList()

        val results = mutableListOf<TransactionDto>()
        for (accountId in validIds) {
            try {
                val response = transactionApi.getTransactions(accountId = accountId)
                if (response.isSuccessful && response.body() != null) {
                    val dtos = response.body()!!.map { backendTx ->
                        TransactionDto(
                            id = backendTx.id,
                            accountId = backendTx.accountId,
                            amount = backendTx.amount,
                            transactionType = backendTx.transactionType,
                            timestamp = backendTx.transactionDate ?: backendTx.createdAt ?: java.time.Instant.now().toString(),
                            description = backendTx.description,
                            providerTransactionId = backendTx.providerReference,
                            createdAt = backendTx.createdAt,
                            updatedAt = backendTx.updatedAt
                        )
                    }
                    results.addAll(dtos)
                }
            } catch (_: Exception) {
                // Tolerate transient network or unlinked account error
            }
        }
        return results
    }

    suspend fun fetchTransactions(accountId: String? = null): List<TransactionDto> {
        if (accountId.isNullOrBlank()) return emptyList()
        return fetchTransactions(listOf(accountId))
    }

    /**
     * Records a new transaction via POST /api/transactions on the Spring Boot backend.
     */
    suspend fun recordTransaction(transaction: TransactionDto): TransactionDto {
        val request = CreateTransactionBackendRequest(
            accountId = transaction.accountId,
            amount = transaction.amount,
            currency = "KES",
            transactionType = transaction.transactionType.uppercase(),
            counterparty = null,
            paymentMethod = null,
            providerReference = transaction.providerTransactionId,
            description = transaction.description,
            categoryId = null,
            transactionDate = transaction.timestamp.takeIf { it.isNotBlank() }
        )
        val response = transactionApi.recordTransaction(request)
        if (response.isSuccessful && response.body() != null) {
            val backendTx = response.body()!!
            return TransactionDto(
                id = backendTx.id,
                accountId = backendTx.accountId,
                amount = backendTx.amount,
                transactionType = backendTx.transactionType,
                timestamp = backendTx.transactionDate ?: backendTx.createdAt ?: transaction.timestamp,
                description = backendTx.description,
                providerTransactionId = backendTx.providerReference,
                createdAt = backendTx.createdAt,
                updatedAt = backendTx.updatedAt
            )
        } else {
            val errorMsg = response.errorBody()?.string()?.trim()
                ?: "Failed to record transaction (HTTP ${response.code()})"
            throw Exception(errorMsg)
        }
    }
}
