package com.example.smartmoney.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Request payload for user registration matching Spring Boot backend expectations.
 * Property names match the backend User / RegisterRequest schema:
 * - `name`: Display name
 * - `email`: Unique email address
 * - `phoneNumber`: Optional contact phone number (7-20 characters)
 * - `password`: Raw password for hashing
 */
@Serializable
data class RegisterRequest(
    @SerialName("name")
    val name: String,

    @SerialName("email")
    val email: String,

    @SerialName("emailAddress")
    val emailAddress: String = email,

    @SerialName("phoneNumber")
    val phoneNumber: String? = null,

    @SerialName("password")
    val password: String
)

/**
 * Request payload for user login authentication.
 */
@Serializable
data class LoginRequest(
    @SerialName("email")
    val email: String,

    @SerialName("emailAddress")
    val emailAddress: String = email,

    @SerialName("password")
    val password: String
)

/**
 * Response payload returned from authentication endpoints.
 * All fields are optional to accommodate various backend response structures
 * (e.g. user entity details, JWT tokens, or status messages).
 */
@Serializable
data class AuthResponse(
    @SerialName("id")
    val id: String? = null,

    @SerialName("userId")
    val userId: String? = null,

    @SerialName("name")
    val name: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("emailAddress")
    val emailAddress: String? = null,

    @SerialName("phoneNumber")
    val phoneNumber: String? = null,

    @SerialName("token")
    val token: String? = null,

    @SerialName("accessToken")
    val accessToken: String? = null,

    @SerialName("tokenType")
    val tokenType: String? = null,

    @SerialName("message")
    val message: String? = null
)

/**
 * Retrofit API interface for authentication endpoints exposed by the Spring Boot backend.
 */
interface AuthApi {

    /**
     * Registers a new user account.
     * Backend endpoint: POST /api/auth/register
     */
    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    /**
     * Authenticates a user and returns session/token details.
     * Backend endpoint: POST /api/auth/login
     */
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>
}
