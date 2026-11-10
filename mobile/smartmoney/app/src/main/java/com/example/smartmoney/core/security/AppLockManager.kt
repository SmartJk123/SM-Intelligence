package com.example.smartmoney.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * High-level status of biometric hardware on the device.
 */
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE
}

/**
 * Central orchestrator for App Lock state, background timeout detection,
 * biometric readiness, and brute-force lockout protection.
 */
class AppLockManager(
    private val securityStorage: SecurityStorageHelper
) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isAppLocked = MutableStateFlow(securityStorage.hasPin())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isPinConfigured = MutableStateFlow(securityStorage.hasPin())
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(securityStorage.isBiometricEnabled)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _lockTimeoutMinutes = MutableStateFlow(securityStorage.lockTimeoutMinutes)
    val lockTimeoutMinutes: StateFlow<Int> = _lockTimeoutMinutes.asStateFlow()

    private val _cooldownSecondsRemaining = MutableStateFlow(0)
    val cooldownSecondsRemaining: StateFlow<Int> = _cooldownSecondsRemaining.asStateFlow()

    private var failedAttempts = 0
    private var cooldownJob: Job? = null
    private var lastBackgroundTimestamp: Long = 0L

    /**
     * Called when the app transitions to background (ON_STOP).
     */
    fun onAppBackgrounded() {
        if (_isPinConfigured.value) {
            lastBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Called when the app resumes to foreground (ON_START).
     */
    fun onAppForegrounded() {
        if (!_isPinConfigured.value) return

        if (lastBackgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
            val thresholdMs = _lockTimeoutMinutes.value * 60_000L

            if (elapsed >= thresholdMs) {
                _isAppLocked.value = true
            }
        }
    }

    /**
     * Manually triggers an immediate lock.
     */
    fun lockNow() {
        if (_isPinConfigured.value) {
            _isAppLocked.value = true
        }
    }

    /**
     * Unlocks the app after successful biometric or PIN verification.
     */
    fun unlock() {
        failedAttempts = 0
        _isAppLocked.value = false
    }

    /**
     * Verifies the entered PIN against the salted hash.
     * Enforces rate limiting: after 5 consecutive failed attempts, locks out for 30 seconds.
     */
    fun verifyPin(inputPin: String): Boolean {
        if (_cooldownSecondsRemaining.value > 0) return false

        val isValid = securityStorage.verifyPin(inputPin)
        if (isValid) {
            unlock()
            return true
        } else {
            failedAttempts++
            if (failedAttempts >= 5) {
                startCooldown(30)
            }
            return false
        }
    }

    /**
     * Sets a new PIN and activates App Lock.
     */
    fun saveNewPin(newPin: String): Boolean {
        val success = securityStorage.savePin(newPin)
        if (success) {
            _isPinConfigured.value = true
            _isAppLocked.value = false
        }
        return success
    }

    /**
     * Removes the PIN and disables App Lock.
     */
    fun removePin() {
        securityStorage.clearPin()
        _isPinConfigured.value = false
        _isBiometricEnabled.value = false
        _isAppLocked.value = false
        failedAttempts = 0
    }

    /**
     * Enables or disables biometric unlock.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        securityStorage.isBiometricEnabled = enabled
        _isBiometricEnabled.value = enabled
    }

    /**
     * Updates the inactivity timeout setting.
     */
    fun setLockTimeoutMinutes(minutes: Int) {
        securityStorage.lockTimeoutMinutes = minutes
        _lockTimeoutMinutes.value = minutes
    }

    /**
     * Probes the Android BiometricManager for biometric hardware capability and enrollment.
     */
    fun checkBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _cooldownSecondsRemaining.value = seconds
        cooldownJob = scope.launch {
            for (sec in seconds downTo 1) {
                _cooldownSecondsRemaining.value = sec
                delay(1000L)
            }
            _cooldownSecondsRemaining.value = 0
            failedAttempts = 0
        }
    }
}
