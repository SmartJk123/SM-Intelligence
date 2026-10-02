package com.example.smartmoney.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smartmoney.MainActivity
import com.example.smartmoney.R
import com.example.smartmoney.data.local.dao.NotificationDao
import com.example.smartmoney.data.local.entity.NotificationEntity
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object NotificationHelper {

    private const val CHANNEL_ID = "kcb_transaction_alerts"
    private const val CHANNEL_NAME = "KCB Banking Alerts"
    private const val CHANNEL_DESC = "Instant notifications for KCB incoming and outgoing transactions"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun postSystemNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        try {
            createNotificationChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Tolerate permission or system notification errors
        }
    }

    suspend fun recordAndAlert(
        context: Context,
        notificationDao: NotificationDao?,
        amount: BigDecimal,
        direction: String,
        reference: String?,
        narration: String? = null
    ) {
        val isCredit = !direction.equals("Debit", ignoreCase = true)
        val formattedAmount = DecimalFormat("#,##0.00").format(amount)
        val title = if (isCredit) "KCB Inflow Received" else "KCB Outflow Paid"
        val refPart = if (!reference.isNullOrBlank()) " (Ref: $reference)" else ""
        val notePart = if (!narration.isNullOrBlank()) " • $narration" else ""
        val message = if (isCredit) {
            "KES $formattedAmount credited to your KCB account$notePart$refPart"
        } else {
            "KES $formattedAmount debited from your KCB account$notePart$refPart"
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val notifId = "notif_" + (reference ?: UUID.randomUUID().toString().take(8))

        if (notificationDao != null) {
            val entity = NotificationEntity(
                id = notifId,
                title = title,
                message = message,
                amount = amount,
                type = if (isCredit) "CREDIT" else "DEBIT",
                timestamp = timestamp,
                reference = reference,
                isRead = false
            )
            notificationDao.insertNotification(entity)
        }

        postSystemNotification(context, title, message)
    }
}
