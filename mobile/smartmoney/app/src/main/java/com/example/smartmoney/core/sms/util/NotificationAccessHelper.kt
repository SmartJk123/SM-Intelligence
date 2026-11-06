package com.example.smartmoney.core.sms.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helper utility to check and request Android Notification Access (NotificationListenerService).
 */
object NotificationAccessHelper {

    /**
     * Checks if the app currently has permission to read notifications via [NotificationListenerService].
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        return try {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            enabledListeners.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an [Intent] to open Android's Notification Access system settings screen.
     */
    fun createNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Opens Android's Notification Access system settings screen.
     */
    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = createNotificationAccessSettingsIntent()
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to general application settings if the specific listener settings intent fails
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Ignore failure
            }
        }
    }
}
