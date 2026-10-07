package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.AuthApi
import com.example.smartmoney.data.remote.api.AuthResponse
import com.example.smartmoney.data.remote.api.LoginRequest
import com.example.smartmoney.data.remote.api.RegisterRequest
import com.example.smartmoney.data.remote.datasource.AuthRemoteDataSource
import com.example.smartmoney.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Concrete implementation of [AuthRepository] communicating with the Spring Boot backend
 * via Retrofit with robust error handling for Render cold starts, HTTP exceptions, and network timeouts.
 */
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource = AuthRemoteDataSource(),
    private val authApi: AuthApi = remoteDataSource.authApi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AuthRepository {

    override suspend fun signUp(
        name: String,
        email: String,
        password: String,
        phoneNumber: String?
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val request = RegisterRequest(
                name = name,
                email = email,
                phoneNumber = phoneNumber,
                password = password
            )
            val response = authApi.register(request)
            if (!response.isSuccessful) {
                throw HttpException(response)
            }
            val body = response.body() ?: AuthResponse()
            remoteDataSource.updateSession(body = body, email = email, fallbackName = name)
            Result.success(Unit)
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("The server is waking up. Please try again in 30 seconds."))
        } catch (e: HttpException) {
            val mappedMessage = parseHttpException(e)
            Result.failure(Exception(mappedMessage))
        } catch (e: IOException) {
            Result.failure(Exception("Network error occurred. Please check your internet connection and try again."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "An unexpected error occurred."))
        }
    }

    override suspend fun signIn(
        email: String,
        password: String
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val request = LoginRequest(
                email = email,
                password = password
            )
            val response = authApi.login(request)
            if (!response.isSuccessful) {
                throw HttpException(response)
            }
            val body = response.body() ?: AuthResponse()
            remoteDataSource.updateSession(body = body, email = email, fallbackName = null)
            Result.success(Unit)
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("The server is waking up. Please try again in 30 seconds."))
        } catch (e: HttpException) {
            val mappedMessage = parseHttpException(e)
            Result.failure(Exception(mappedMessage))
        } catch (e: IOException) {
            Result.failure(Exception("Network error occurred. Please check your internet connection and try again."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "An unexpected error occurred."))
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(dispatchers.io) {
        remoteDataSource.signOut()
    }

    override fun currentUserId(): String? = remoteDataSource.currentUserId()

    override fun currentUserName(): String? = remoteDataSource.currentUserName()

    override fun currentUserEmail(): String? = remoteDataSource.currentUserEmail()

    override fun currentUserPhoneNumber(): String? = remoteDataSource.currentUserPhoneNumber()

    override fun isUserLoggedIn(): Boolean = remoteDataSource.isUserLoggedIn()

    override fun observeAuthState(): Flow<Boolean> = remoteDataSource.observeAuthState()

    /**
     * Extracts the HTTP error code and parses the response body to surface exact Spring Boot error messages.
     * Provides clear, user-friendly fallback messages if parsing fails or body is empty.
     */
    private fun parseHttpException(e: HttpException): String {
        val statusCode = e.code()
        val errorBody = try {
            e.response()?.errorBody()?.string()?.trim()
        } catch (_: Exception) {
            null
        }

        if (!errorBody.isNullOrBlank()) {
            try {
                if (errorBody.startsWith("{") && errorBody.endsWith("}")) {
                    val jsonElement = Json.parseToJsonElement(errorBody)
                    if (jsonElement is JsonObject) {
                        val message = (jsonElement["message"] as? JsonPrimitive)?.content
                        if (!message.isNullOrBlank()) return message

                        val details = jsonElement["details"] as? JsonObject
                        if (details != null && details.isNotEmpty()) {
                            val detailMsgs = details.values
                                .mapNotNull { (it as? JsonPrimitive)?.content }
                                .filter { it.isNotBlank() }
                            if (detailMsgs.isNotEmpty()) return detailMsgs.joinToString(", ")
                        }

                        val error = (jsonElement["error"] as? JsonPrimitive)?.content
                        if (!error.isNullOrBlank()) return error
                    }
                } else if (!errorBody.startsWith("<")) {
                    return errorBody
                }
            } catch (_: Exception) {
                // Parsing failed; fall through to status code based fallbacks
            }
        }

        return when (statusCode) {
            400 -> "Invalid request. Please verify your details."
            401 -> "Invalid email or password."
            403 -> "Access denied. You do not have permission to perform this action."
            404 -> "Authentication endpoint not found."
            409 -> "An account with this email already exists."
            500 -> "Internal server error. Please try again later."
            502, 503, 504 -> "The server is waking up. Please try again in 30 seconds."
            else -> "Server error ($statusCode). Please try again."
        }
    }
}
