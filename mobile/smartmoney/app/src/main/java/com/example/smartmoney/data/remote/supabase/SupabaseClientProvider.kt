package com.example.smartmoney.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.android.Android

/**
 * Thread-safe Singleton provider for SupabaseClient.
 * Configures Postgrest and Auth plugins using Android Ktor engine.
 */
object SupabaseClientProvider {

    @Volatile
    private var instance: SupabaseClient? = null

    fun getClient(): SupabaseClient {
        return instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = SupabaseConfig.supabaseUrl,
                supabaseKey = SupabaseConfig.supabaseAnonKey
            ) {
                httpEngine = Android.create()
                install(Postgrest)
                install(Auth)
            }.also { instance = it }
        }
    }
}
