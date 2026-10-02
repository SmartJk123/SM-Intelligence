package com.example.smartmoney

import com.example.smartmoney.data.remote.RetrofitClient
import com.example.smartmoney.data.remote.api.AuthResponse
import com.example.smartmoney.data.remote.api.LoginRequest
import com.example.smartmoney.data.remote.api.RegisterRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun registerRequest_serializes_to_exact_backend_property_names() {
        val request = RegisterRequest(
            name = "John Doe",
            email = "john.doe@example.com",
            phoneNumber = "+254712345678",
            password = "SecurePassword123!"
        )

        val jsonString = json.encodeToString(request)
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)

        // Verify exact property names matching backend RegisterRequest / User entity:
        // - name
        // - email
        // - phoneNumber (camelCase)
        // - password
        assertTrue(jsonObject.containsKey("name"))
        assertTrue(jsonObject.containsKey("email"))
        assertTrue(jsonObject.containsKey("emailAddress"))
        assertTrue(jsonObject.containsKey("phoneNumber"))
        assertTrue(jsonObject.containsKey("password"))

        assertEquals("John Doe", jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("john.doe@example.com", jsonObject["email"]?.jsonPrimitive?.content)
        assertEquals("john.doe@example.com", jsonObject["emailAddress"]?.jsonPrimitive?.content)
        assertEquals("+254712345678", jsonObject["phoneNumber"]?.jsonPrimitive?.content)
        assertEquals("SecurePassword123!", jsonObject["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun loginRequest_serializes_to_exact_property_names() {
        val request = LoginRequest(
            email = "john.doe@example.com",
            password = "SecurePassword123!"
        )

        val jsonString = json.encodeToString(request)
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)

        assertTrue(jsonObject.containsKey("email"))
        assertTrue(jsonObject.containsKey("emailAddress"))
        assertTrue(jsonObject.containsKey("password"))
        assertEquals("john.doe@example.com", jsonObject["email"]?.jsonPrimitive?.content)
        assertEquals("john.doe@example.com", jsonObject["emailAddress"]?.jsonPrimitive?.content)
        assertEquals("SecurePassword123!", jsonObject["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun authResponse_deserializes_from_backend_user_or_token_payload() {
        val rawJson = """
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "John Doe",
                "email": "john.doe@example.com",
                "phoneNumber": "+254712345678",
                "token": "mock-jwt-token-12345",
                "tokenType": "Bearer",
                "message": "Registration successful"
            }
        """.trimIndent()

        val response = json.decodeFromString<AuthResponse>(rawJson)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.id)
        assertEquals("John Doe", response.name)
        assertEquals("john.doe@example.com", response.email)
        assertEquals("+254712345678", response.phoneNumber)
        assertEquals("mock-jwt-token-12345", response.token)
        assertEquals("Bearer", response.tokenType)
        assertEquals("Registration successful", response.message)
    }

    @Test
    fun authResponse_deserializes_from_spring_boot_backend_payload() {
        val rawJson = """
            {
                "userId": "550e8400-e29b-41d4-a716-446655440000",
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Jane Mwangi",
                "emailAddress": "jane@example.com",
                "email": "jane@example.com",
                "phoneNumber": "+254711223344",
                "token": "spring-jwt-token-67890",
                "tokenType": "Bearer"
            }
        """.trimIndent()

        val response = json.decodeFromString<AuthResponse>(rawJson)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.userId)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.id)
        assertEquals("Jane Mwangi", response.name)
        assertEquals("jane@example.com", response.emailAddress)
        assertEquals("jane@example.com", response.email)
        assertEquals("+254711223344", response.phoneNumber)
        assertEquals("spring-jwt-token-67890", response.token)
    }

    @Test
    fun retrofitClient_initializes_and_exposes_authApi() {
        assertNotNull(RetrofitClient.authApi)
    }
}
