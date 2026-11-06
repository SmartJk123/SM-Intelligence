package com.example.smartmoney.core.sms.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import com.example.smartmoney.SmartMoneyApplication
import com.example.smartmoney.core.notification.NotificationHelper
import com.example.smartmoney.core.sms.ingestion.MpesaIngestionResult
import com.example.smartmoney.core.sms.model.ParsedFinancialMovement
import com.example.smartmoney.core.sms.parser.MpesaTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background notification listener that intercepts incoming M-PESA confirmation notifications
 * posted by SMS messaging apps (Google Messages, Samsung Messages) or official M-PESA applications.
 *
 * Runs 100% offline with zero external network connectivity and requires no restricted SMS permissions.
 */
class MpesaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parser = MpesaTransactionParser()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                onNotificationPosted(sbn)
            }
        } catch (_: Exception) {
            // Ignore runtime errors on listener connection
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Avoid infinite feedback loops by ignoring notifications posted by SmartMoney itself
        if (sbn.packageName == packageName) return

        // 2. Fast pre-filter to ignore notifications with no financial or M-PESA indicators
        if (!NotificationContentExtractor.isCandidateNotification(sbn)) return

        // 3. Extract candidate message bodies from notification extras
        val candidateTexts = NotificationContentExtractor.extractCandidateTexts(sbn)
        if (candidateTexts.isEmpty()) return

        // 4. Parse candidates with MpesaTransactionParser (requires Android O+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        var matchedMovement: ParsedFinancialMovement? = null
        for (candidate in candidateTexts) {
            val movement = parser.parse(candidate)
            if (movement != null) {
                matchedMovement = movement
                break
            }
        }

        val movement = matchedMovement ?: return

        // 5. Ingest parsed movement into Room database
        val app = applicationContext as? SmartMoneyApplication ?: return
        val ingestionService = app.container.mpesaTransactionIngestionService
        val notificationDao = app.container.database.notificationDao()

        // Acquire a brief CPU wake lock (safety timeout: 10 seconds) to ensure the CPU
        // does not go to sleep while Room writes to SQLite when the screen is locked.
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "smartmoney:mpesa_notification_ingest"
        )?.apply {
            setReferenceCounted(false)
            acquire(10_000)
        }

        serviceScope.launch {
            try {
                when (val result = ingestionService.ingest(movement)) {
                    is MpesaIngestionResult.Success -> {
                        NotificationHelper.recordMpesaIngestionAlert(
                            context = applicationContext,
                            notificationDao = notificationDao,
                            amount = movement.amount,
                            direction = movement.direction.name,
                            reference = movement.reference,
                            description = result.transaction.description
                        )
                    }

                    is MpesaIngestionResult.Duplicate -> {
                        // Already imported; silent deduplication
                    }

                    is MpesaIngestionResult.MissingAccount -> {
                        NotificationHelper.postSystemNotification(
                            context = applicationContext,
                            title = "M-PESA Ingestion Paused",
                            message = "Transaction ${movement.reference} detected, but no M-PESA account is linked. Please link your M-PESA account."
                        )
                    }

                    is MpesaIngestionResult.AmbiguousAccount -> {
                        NotificationHelper.postSystemNotification(
                            context = applicationContext,
                            title = "M-PESA Ambiguity",
                            message = "Multiple M-PESA accounts detected. Please specify your target account in settings."
                        )
                    }

                    is MpesaIngestionResult.InvalidMovement,
                    is MpesaIngestionResult.DatabaseError -> {
                        // Tolerate unparsable movements or persistence errors without crashing the service
                    }
                }
            } catch (_: Exception) {
                // Ignore unexpected runtime failures in background notification listener
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {
                    // Ignore release errors
                }
            }
        }
    }
}
