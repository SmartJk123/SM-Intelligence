package com.example.smartmoney.domain.model

import androidx.compose.runtime.Immutable

/**
 * Pure domain model representing an application user profile.
 * Credentials and authentication tokens are managed by Supabase Auth (auth.users),
 * NOT stored in application user models or tables.
 */
@Immutable
data class User(
    val id: String,
    val name: String,
    val emailAddress: String,
    val phoneNumber: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
