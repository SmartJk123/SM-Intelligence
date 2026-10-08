package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.remote.dto.TransactionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class TransactionRemoteDataSource(private val client: SupabaseClient) {

    suspend fun fetchTransactions(accountId: String? = null): List<TransactionDto> {
        return client.postgrest["transactions"].select {
            filter {
                if (accountId != null) {
                    eq("account_id", accountId)
                }
            }
        }.decodeList<TransactionDto>()
    }

    suspend fun recordTransaction(transaction: TransactionDto): TransactionDto {
        return client.postgrest["transactions"].insert(transaction) {
            select()
        }.decodeSingle<TransactionDto>()
    }
}
