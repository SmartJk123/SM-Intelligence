package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.remote.dto.AccountConnectionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class AccountConnectionRemoteDataSource(private val client: SupabaseClient) {

    suspend fun fetchConnections(accountId: String): List<AccountConnectionDto> {
        return client.postgrest["account_connections"].select {
            filter {
                eq("account_id", accountId)
            }
        }.decodeList<AccountConnectionDto>()
    }
}
