package com.example.smartmoney.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton managing user profile picture caching and persistence in app-internal storage.
 *
 * Implements main-safe coroutine suspend functions offloading disk I/O and bitmap decoding
 * onto [Dispatchers.IO] to ensure zero UI jank or frame drops during app launch and photo picking.
 *
 * Provides reactive [StateFlow] updates so all UI components (TitleBar, Settings, Hub)
 * update synchronously when a picture is uploaded or removed.
 */
object UserProfileManager {
    private val _profileBitmap = MutableStateFlow<ImageBitmap?>(null)
    val profileBitmap: StateFlow<ImageBitmap?> = _profileBitmap.asStateFlow()

    @Volatile
    private var currentUserId: String? = null

    fun getAvatarFile(context: Context, userId: String): File {
        return File(context.filesDir, "user_profile_${userId}_avatar.jpg")
    }

    /**
     * Switches the active user, loading their specific profile avatar if present.
     * If [userId] is null or the user has no saved avatar, resets the state to null (displaying fallback initials).
     * Main-safe: runs disk I/O and bitmap decoding on [dispatcher].
     */
    suspend fun switchUser(
        context: Context,
        userId: String?,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        currentUserId = userId
        if (userId.isNullOrBlank()) {
            _profileBitmap.value = null
            return@withContext
        }
        val file = getAvatarFile(context, userId)
        if (file.exists() && file.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                _profileBitmap.value = bitmap
            } catch (_: Exception) {
                _profileBitmap.value = null
            }
        } else {
            _profileBitmap.value = null
        }
    }

    /**
     * Initializes the manager for backward compatibility. Delegates to [switchUser].
     */
    suspend fun initialize(
        context: Context,
        userId: String? = currentUserId,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = switchUser(context, userId, dispatcher)

    /**
     * Copies the picked image from the given URI to internal app storage scoped to [userId] on [dispatcher]
     * and updates the reactive [profileBitmap] state.
     */
    suspend fun updateProfilePicture(
        context: Context,
        uri: Uri,
        userId: String? = currentUserId,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(dispatcher) {
        val targetUserId = userId ?: currentUserId
        if (targetUserId.isNullOrBlank()) return@withContext false

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val file = getAvatarFile(context, targetUserId)
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                _profileBitmap.value = bitmap
                currentUserId = targetUserId
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Deletes the user-scoped profile picture on [dispatcher] and resets the state to fallback initials.
     */
    suspend fun removeProfilePicture(
        context: Context,
        userId: String? = currentUserId,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(dispatcher) {
        val targetUserId = userId ?: currentUserId
        try {
            if (!targetUserId.isNullOrBlank()) {
                val file = getAvatarFile(context, targetUserId)
                if (file.exists()) {
                    file.delete()
                }
            }
            _profileBitmap.value = null
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Wipes active session memory state, clearing current user and avatar bitmap.
     */
    fun clearSession() {
        currentUserId = null
        _profileBitmap.value = null
    }

    /**
     * Resets the in-memory bitmap state, primarily for testing purposes.
     */
    fun resetForTesting() {
        clearSession()
    }
}

