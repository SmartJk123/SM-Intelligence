package com.example.smartmoney.core.security

import android.util.Base64
import org.json.JSONObject

/**
 * Utility for parsing and validating JSON Web Tokens (JWT) locally on the client.
 * Decodes the unencrypted payload section without verifying the cryptographic signature,
 * enabling proactive client-side token expiration checks on cold start.
 */
object JwtUtils {

    /**
     * Extracts the 'exp' (expiration timestamp in epoch seconds) claim from the JWT payload.
     * Handles both epoch seconds (10-digit) and epoch milliseconds (13-digit) formats.
     * Returns null if the token is malformed or lacks an 'exp' claim.
     */
    fun getExpirationSeconds(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val cleanToken = token.removePrefix("Bearer ").removePrefix("bearer ").trim()
        return try {
            val parts = cleanToken.split(".")
            if (parts.size < 2) return null

            val payloadBase64 = parts[1]
            val decodedBytes = Base64.decode(payloadBase64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jsonString = String(decodedBytes, Charsets.UTF_8)
            val jsonObject = JSONObject(jsonString)

            if (jsonObject.has("exp")) {
                val expRaw = jsonObject.getLong("exp")
                if (expRaw > 10_000_000_000L) expRaw / 1000 else expRaw
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates whether [token] has expired.
     * Returns true only if an 'exp' claim exists and current time has passed it.
     */
    fun isTokenExpired(token: String?): Boolean {
        if (token.isNullOrBlank()) return true
        val expSeconds = getExpirationSeconds(token) ?: return false
        val currentSeconds = System.currentTimeMillis() / 1000
        return currentSeconds >= expSeconds
    }

    /**
     * Extracts the subject claim ('sub') or 'userId' from the JWT payload.
     */
    fun getSubject(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val cleanToken = token.removePrefix("Bearer ").removePrefix("bearer ").trim()
        return try {
            val parts = cleanToken.split(".")
            if (parts.size < 2) return null

            val payloadBase64 = parts[1]
            val decodedBytes = Base64.decode(payloadBase64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jsonObject = JSONObject(String(decodedBytes, Charsets.UTF_8))

            when {
                jsonObject.has("sub") -> jsonObject.getString("sub")
                jsonObject.has("userId") -> jsonObject.getString("userId")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
