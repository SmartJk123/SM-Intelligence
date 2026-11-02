package com.example.smartmoney.data.remote.datasource

import com.example.smartmoney.data.local.UserPreferencesRepository
import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.AuthApi
import com.example.smartmoney.data.remote.api.AuthResponse
import com.example.smartmoney.data.remote.api.LoginRequest
import com.example.smartmoney.data.remote.api.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

/**
 * Remote data source routing authentication directly through the Render Gateway via Retrofit.
 * Completely eliminates any client-side Supabase GoTrue calls and manages session persistence.
 */
class AuthRemoteDataSource(
    val authApi: AuthApi = RetrofitClient.authApi,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) {

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: Flow<Boolean> = _isLoggedInFlow.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentUserEmail: String? = null
    private var currentUserPhoneNumber: String? = null
    private var authToken: String? = null

    init {
        RetrofitClient.tokenProvider = { authToken }

        // Restore persisted session from storage on cold start
        val savedToken = userPreferencesRepository?.getAuthToken()
        val savedUserId = userPreferencesRepository?.getUserId()
        if (!savedToken.isNullOrBlank() && !savedUserId.isNullOrBlank()) {
            authToken = savedToken
            currentUserId = savedUserId
            currentUserName = userPreferencesRepository?.getUserName()
            currentUserEmail = userPreferencesRepository?.getUserEmail()
            currentUserPhoneNumber = userPreferencesRepository?.getUserPhoneNumber()
            _isLoggedInFlow.value = true
        }
    }

    /**
     * Registers a new user via the backend endpoint POST /api/auth/register.
     */
    suspend fun register(
        name: String,
        email: String,
        phoneNumber: String?,
        password: String
    ): Result<AuthResponse> {
        return try {
            val request = RegisterRequest(
                name = name,
                email = email,
                phoneNumber = phoneNumber,
                password = password
            )
            val response = authApi.register(request)
            handleAuthResponse(response, email = email, fallbackName = name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates an existing user via the backend endpoint POST /api/auth/login.
     */
    suspend fun login(
        email: String,
        password: String
    ): Result<AuthResponse> {
        return try {
            val request = LoginRequest(
                email = email,
                password = password
            )
            val response = authApi.login(request)
            handleAuthResponse(response, email = email, fallbackName = null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convenience alias for registration.
     */
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        phoneNumber: String? = null
    ): Result<AuthResponse> = register(name, email, phoneNumber, password)

    /**
     * Convenience alias for sign in.
     */
    suspend fun signIn(
        email: String,
        password: String
    ): Result<AuthResponse> = login(email, password)

    /**
     * Validates the active session against GET /api/auth/me on app launch.
     */
    suspend fun validateRemoteSession(): Result<Unit> {
        if (authToken.isNullOrBlank()) {
            signOut()
            return Result.failure(Exception("No active session token"))
        }
        return try {
            val response = authApi.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                currentUserName = user.name ?: currentUserName
                currentUserEmail = user.emailAddress ?: currentUserEmail
                currentUserPhoneNumber = user.phoneNumber ?: currentUserPhoneNumber
                userPreferencesRepository?.saveAuthSession(authToken, currentUserId, currentUserName, currentUserEmail, currentUserPhoneNumber)
                Result.success(Unit)
            } else if (response.code() == 401 || response.code() == 403) {
                signOut()
                Result.failure(Exception("Session token has expired"))
            } else {
                // Keep local session if server returns transient error
                Result.success(Unit)
            }
        } catch (e: Exception) {
            // Keep local offline session if server is temporarily unreachable
            Result.success(Unit)
        }
    }

    /**
     * Logs out the user, clearing session tokens and storage.
     */
    suspend fun signOut(): Result<Unit> {
        currentUserId = null
        currentUserName = null
        currentUserEmail = null
        currentUserPhoneNumber = null
        authToken = null
        _isLoggedInFlow.value = false
        userPreferencesRepository?.clearAuthSession()
        return Result.success(Unit)
    }

    fun currentUserId(): String? = currentUserId

    fun currentUserName(): String? = currentUserName

    fun currentUserEmail(): String? = currentUserEmail

    fun currentUserPhoneNumber(): String? = currentUserPhoneNumber

    fun authToken(): String? = authToken

    fun isUserLoggedIn(): Boolean = _isLoggedInFlow.value

    fun observeAuthState(): Flow<Boolean> = _isLoggedInFlow.asStateFlow()

    /**
     * Updates local session details after successful authentication.
     */
    fun updateSession(
        body: AuthResponse,
        email: String,
        fallbackName: String?
    ) {
        currentUserId = body.userId ?: body.id ?: java.util.UUID.randomUUID().toString()
        currentUserName = body.name ?: fallbackName ?: email.substringBefore('@')
        currentUserEmail = body.email ?: body.emailAddress ?: email
        currentUserPhoneNumber = body.phoneNumber
        authToken = body.token ?: body.accessToken
        _isLoggedInFlow.value = true

        userPreferencesRepository?.let { prefs ->
            CoroutineScope(Dispatchers.IO).launch {
                prefs.saveAuthSession(authToken, currentUserId, currentUserName, currentUserEmail, currentUserPhoneNumber)
            }
        }
    }

    /**
     * Unpacks Retrofit responses cleanly, parsing error bodies on non-2xx HTTP codes.
     */
    private fun handleAuthResponse(
        response: Response<AuthResponse>,
        email: String,
        fallbackName: String?
    ): Result<AuthResponse> {
        return if (response.isSuccessful) {
            val body = response.body() ?: AuthResponse()
            updateSession(body, email, fallbackName)
            Result.success(body)
        } else {
            val errorBodyString = response.errorBody()?.string()?.trim()
            val errorMessage = parseErrorMessage(errorBodyString, response.code(), response.message())
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Parses error responses from Spring Boot (JSON with "message" or plain text error).
     */
    private fun parseErrorMessage(
        errorBody: String?,
        statusCode: Int,
        statusMessage: String
    ): String {
        if (errorBody.isNullOrBlank()) {
            return "HTTP $statusCode: $statusMessage"
        }
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
            }
        } catch (_: Exception) {
            // If JSON parsing fails, fall back to the raw error body text
        }
        return errorBody
    }
}
