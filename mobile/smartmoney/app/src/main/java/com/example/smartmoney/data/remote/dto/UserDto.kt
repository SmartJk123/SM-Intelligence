package com.example.smartmoney.data.remote.dto

import com.example.smartmoney.domain.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("email_address") val emailAddress: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
        fun fromDomain(user: User): UserDto = UserDto(
            id = user.id,
            name = user.name,
            emailAddress = user.emailAddress,
            phoneNumber = user.phoneNumber,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}