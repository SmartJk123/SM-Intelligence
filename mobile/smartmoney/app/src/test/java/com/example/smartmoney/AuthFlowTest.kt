package com.example.smartmoney

import com.example.smartmoney.data.remote.api.AuthApi
import com.example.smartmoney.data.remote.api.AuthResponse
import com.example.smartmoney.data.remote.api.LoginRequest
import com.example.smartmoney.data.remote.api.RegisterRequest
import com.example.smartmoney.data.remote.datasource.AuthRemoteDataSource
import com.example.smartmoney.data.repository.AuthRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AuthFlowTest {

    private class FakeAuthApi(
        var registerResponse: Response<AuthResponse>? = null,
        var loginResponse: Response<AuthResponse>? = null,
        var shouldThrow: Exception? = null
    ) : AuthApi {
        var lastRegisterRequest: RegisterRequest? = null
        var lastLoginRequest: LoginRequest? = null

        override suspend fun register(request: RegisterRequest): Response<AuthResponse> {
            lastRegisterRequest = request
            shouldThrow?.let { throw it }
            return registerResponse ?: Response.success(
                AuthResponse(
                    id = "user-123",
                    name = request.name,
                    email = request.email,
                    phoneNumber = request.phoneNumber,
                    token = "token-xyz"
                )
            )
        }

        override suspend fun login(request: LoginRequest): Response<AuthResponse> {
            lastLoginRequest = request
            shouldThrow?.let { throw it }
            return loginResponse ?: Response.success(
                AuthResponse(
                    id = "user-123",
                    name = "Existing User",
                    email = request.email,
                    token = "token-xyz"
                )
            )
        }
    }

    @Test
    fun register_success_updates_session_and_passes_exact_parameters() = runBlocking {
        val fakeApi = FakeAuthApi()
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signUp(
            name = "Jane Mwangi",
            email = "jane@example.com",
            password = "Password123!",
            phoneNumber = "+254711223344"
        )

        assertTrue(result.isSuccess)
        assertNotNull(fakeApi.lastRegisterRequest)
        assertEquals("Jane Mwangi", fakeApi.lastRegisterRequest?.name)
        assertEquals("jane@example.com", fakeApi.lastRegisterRequest?.email)
        assertEquals("+254711223344", fakeApi.lastRegisterRequest?.phoneNumber)
        assertEquals("Password123!", fakeApi.lastRegisterRequest?.password)

        assertTrue(repository.isUserLoggedIn())
        assertEquals("user-123", repository.currentUserId())
        assertEquals("Jane Mwangi", repository.currentUserName())
        assertEquals("jane@example.com", repository.currentUserEmail())
        assertTrue(repository.observeAuthState().first())
    }

    @Test
    fun login_success_updates_session_and_passes_credentials() = runBlocking {
        val fakeApi = FakeAuthApi()
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signIn(
            email = "jane@example.com",
            password = "Password123!"
        )

        assertTrue(result.isSuccess)
        assertNotNull(fakeApi.lastLoginRequest)
        assertEquals("jane@example.com", fakeApi.lastLoginRequest?.email)
        assertEquals("Password123!", fakeApi.lastLoginRequest?.password)

        assertTrue(repository.isUserLoggedIn())
        assertEquals("user-123", repository.currentUserId())
        assertEquals("Existing User", repository.currentUserName())
    }

    @Test
    fun register_failure_unpacks_backend_error_json() = runBlocking {
        val jsonError = """{"message":"User with this email already exists"}"""
        val errorResponse = Response.error<AuthResponse>(
            400,
            jsonError.toResponseBody("application/json".toMediaType())
        )
        val fakeApi = FakeAuthApi(registerResponse = errorResponse)
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signUp(
            name = "Jane",
            email = "duplicate@example.com",
            password = "Secret"
        )

        assertTrue(result.isFailure)
        assertEquals("User with this email already exists", result.exceptionOrNull()?.message)
        assertFalse(repository.isUserLoggedIn())
    }

    @Test
    fun login_failure_unpacks_plain_text_error() = runBlocking {
        val textError = "Invalid email or password credentials"
        val errorResponse = Response.error<AuthResponse>(
            401,
            textError.toResponseBody("text/plain".toMediaType())
        )
        val fakeApi = FakeAuthApi(loginResponse = errorResponse)
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signIn(
            email = "jane@example.com",
            password = "WrongPassword"
        )

        assertTrue(result.isFailure)
        assertEquals("Invalid email or password credentials", result.exceptionOrNull()?.message)
        assertFalse(repository.isUserLoggedIn())
    }

    @Test
    fun signOut_clears_session_state() = runBlocking {
        val fakeApi = FakeAuthApi()
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        repository.signIn("jane@example.com", "Password123!")
        assertTrue(repository.isUserLoggedIn())

        repository.signOut()
        assertFalse(repository.isUserLoggedIn())
        assertEquals(null, repository.currentUserId())
        assertEquals(null, repository.currentUserName())
        assertFalse(repository.observeAuthState().first())
    }

    @Test
    fun login_timeout_maps_to_server_waking_up_message() = runBlocking {
        val fakeApi = FakeAuthApi(shouldThrow = java.net.SocketTimeoutException("Read timed out"))
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signIn("jane@example.com", "Password123!")
        assertTrue(result.isFailure)
        assertEquals(
            "The server is waking up. Please try again in 30 seconds.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun register_network_failure_maps_to_connectivity_message() = runBlocking {
        val fakeApi = FakeAuthApi(shouldThrow = java.io.IOException("Unable to resolve host"))
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signUp("Jane", "jane@example.com", "Password123!")
        assertTrue(result.isFailure)
        assertEquals(
            "Network error occurred. Please check your internet connection and try again.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun http_502_maps_to_server_waking_up_message() = runBlocking {
        val errorResponse = Response.error<AuthResponse>(
            502,
            "<html><body>502 Bad Gateway</body></html>".toResponseBody("text/html".toMediaType())
        )
        val fakeApi = FakeAuthApi(loginResponse = errorResponse)
        val dataSource = AuthRemoteDataSource(fakeApi)
        val repository = AuthRepositoryImpl(dataSource)

        val result = repository.signIn("jane@example.com", "Password123!")
        assertTrue(result.isFailure)
        assertEquals(
            "The server is waking up. Please try again in 30 seconds.",
            result.exceptionOrNull()?.message
        )
    }
}
