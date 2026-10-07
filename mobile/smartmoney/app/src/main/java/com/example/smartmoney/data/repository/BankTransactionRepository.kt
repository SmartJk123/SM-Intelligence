package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.BankTransactionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BankTransactionRepository(
    private val userId: String,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {
    private val _kcbTransactions = MutableStateFlow<List<BankTransactionResponse>>(emptyList())
    val kcbTransactions: StateFlow<List<BankTransactionResponse>> = _kcbTransactions.asStateFlow()

    suspend fun refreshKcbTransactions(since: String? = null): Result<List<BankTransactionResponse>> =
        withContext(dispatchers.io) {
            try {
                val response = RetrofitClient.bankIntegrationApi.getKcbTransactions(userId, since)
                if (response.isSuccessful) {
                    val transactions = response.body() ?: emptyList()
                    _kcbTransactions.value = transactions
                    Result.success(transactions)
                } else {
                    Result.failure(Exception("Failed to fetch KCB transactions: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
