package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.remote.dto.UserDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class UserRemoteDataSource(private val client: SupabaseClient) {

    suspend fun fetchUserProfile(userId: String): UserDto? {
        return client.postgrest["users"].select {
            filter {
                eq("id", userId)
            }
        }.decodeSingleOrNull<UserDto>()
    }

    suspend fun updateUserProfile(user: UserDto) {
        client.postgrest["users"].update(user) {
            filter {
                eq("id", user.id)
            }
        }
    }
}
