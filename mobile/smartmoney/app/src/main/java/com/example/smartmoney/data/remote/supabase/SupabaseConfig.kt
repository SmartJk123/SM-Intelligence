package com.example.smartmoney.data.remote.supabase

import com.example.smartmoney.BuildConfig

/**
 * Retrieves Supabase configuration securely from BuildConfig (populated from local.properties / environment).
 * Privileged service-role keys must NEVER be placed here or in the Android client.
 */
object SupabaseConfig {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY
}
