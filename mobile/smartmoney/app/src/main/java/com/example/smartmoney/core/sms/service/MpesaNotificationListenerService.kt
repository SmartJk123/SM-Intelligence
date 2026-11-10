package com.example.smartmoney.core.sms.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import com.example.smartmoney.SmartMoneyApplication
import com.example.smartmoney.core.notification.NotificationHelper
import com.example.smartmoney.core.sms.ingestion.BankIngestionResult
import com.example.smartmoney.core.sms.ingestion.MpesaIngestionResult
import com.example.smartmoney.core.sms.model.ParsedBankMovement
import com.example.smartmoney.core.sms.model.ParsedFinancialMovement
import com.example.smartmoney.core.sms.parser.BankTransactionParser
import com.example.smartmoney.core.sms.parser.MpesaTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background notification listener that intercepts incoming M-PESA and Bank (KCB & NCBA) confirmation notifications
 * posted by SMS messaging apps (Google Messages, Samsung Messages) or official financial applications.
 *
 * Runs 100% offline with zero external network connectivity and requires no restricted SMS permissions.
 */
class MpesaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mpesaParser = MpesaTransactionParser()
    private val bankParser = BankTransactionParser()

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

        // 2. Fast pre-filter to ignore notifications with no financial or M-PESA/Bank indicators
        if (!NotificationContentExtractor.isCandidateNotification(sbn)) return

        // 3. Extract candidate message bodies from notification extras
        val candidateTexts = NotificationContentExtractor.extractCandidateTexts(sbn)
        if (candidateTexts.isEmpty()) return

        // 4. Parse candidates with M-PESA or Bank parsers (requires Android O+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        var matchedMpesaMovement: ParsedFinancialMovement? = null
        var matchedBankMovement: ParsedBankMovement? = null

        for (candidate in candidateTexts) {
            val mpesaMovement = mpesaParser.parse(candidate)
            if (mpesaMovement != null) {
                matchedMpesaMovement = mpesaMovement
                break
            }

            val bankMovement = bankParser.parse(candidate)
            if (bankMovement != null) {
                matchedBankMovement = bankMovement
                break
            }
        }

        if (matchedMpesaMovement == null && matchedBankMovement == null) return

        // 5. Ingest parsed movement into Room database
        val app = applicationContext as? SmartMoneyApplication ?: return
        val notificationDao = app.container.database.notificationDao()

        // Acquire a brief CPU wake lock (safety timeout: 10 seconds) to ensure the CPU
        // does not go to sleep while Room writes to SQLite when the screen is locked.
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "smartmoney:financial_notification_ingest"
        )?.apply {
            setReferenceCounted(false)
            acquire(10_000)
        }

        serviceScope.launch {
            try {
                // A. Handle M-PESA transaction
                if (matchedMpesaMovement != null) {
                    val mpesaService = app.container.mpesaTransactionIngestionService
                    when (val result = mpesaService.ingest(matchedMpesaMovement)) {
                        is MpesaIngestionResult.Success -> {
                            NotificationHelper.recordMpesaIngestionAlert(
                                context = applicationContext,
                                notificationDao = notificationDao,
                                amount = matchedMpesaMovement.amount,
                                direction = matchedMpesaMovement.direction.name,
                                reference = matchedMpesaMovement.reference,
                                description = result.transaction.description
                            )
                        }

                        is MpesaIngestionResult.Duplicate -> {
                            // Silent deduplication
                        }

                        is MpesaIngestionResult.MissingAccount -> {
                            NotificationHelper.postSystemNotification(
                                context = applicationContext,
                                title = "M-PESA Ingestion Paused",
                                message = "Transaction ${matchedMpesaMovement.reference} detected, but no M-PESA account is linked. Please link your M-PESA account."
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
                }

                // B. Handle Commercial Bank transaction (KCB or NCBA)
                if (matchedBankMovement != null) {
                    val bankService = app.container.bankTransactionIngestionService
                    val bankDisplayName = matchedBankMovement.institution.displayName
                    when (val result = bankService.ingest(matchedBankMovement)) {
                        is BankIngestionResult.Success -> {
                            NotificationHelper.recordBankIngestionAlert(
                                context = applicationContext,
                                notificationDao = notificationDao,
                                bankName = bankDisplayName,
                                amount = matchedBankMovement.amount,
                                direction = matchedBankMovement.direction.name,
                                reference = matchedBankMovement.reference,
                                description = result.transaction.description
                            )
                        }

                        is BankIngestionResult.Duplicate -> {
                            // Silent deduplication
                        }

                        is BankIngestionResult.MissingAccount -> {
                            NotificationHelper.postSystemNotification(
                                context = applicationContext,
                                title = "$bankDisplayName Ingestion Paused",
                                message = "Transaction ${matchedBankMovement.reference} detected, but no $bankDisplayName account is linked."
                            )
                        }

                        is BankIngestionResult.AmbiguousAccount -> {
                            NotificationHelper.postSystemNotification(
                                context = applicationContext,
                                title = "$bankDisplayName Ambiguity",
                                message = "Multiple $bankDisplayName accounts detected. Please specify your target account in settings."
                            )
                        }

                        is BankIngestionResult.InvalidMovement,
                        is BankIngestionResult.DatabaseError -> {
                            // Tolerate persistence errors safely
                        }
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
