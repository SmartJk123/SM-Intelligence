package com.example.smartmoney.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smartmoney.domain.model.User

/**
 * Local Room cache entity for public.users profile data.
 * Credentials and passwords are NEVER stored in this entity.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val emailAddress: String,
    val phoneNumber: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toDomain(): User = User(
        id = id,
        name = name,
        emailAddress = emailAddress,
        phoneNumber = phoneNumber,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(user: User): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            emailAddress = user.emailAddress,
            phoneNumber = user.phoneNumber,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}
