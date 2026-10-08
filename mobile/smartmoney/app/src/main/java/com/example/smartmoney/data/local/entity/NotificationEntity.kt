package com.example.smartmoney.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smartmoney.domain.model.Notification
import java.math.BigDecimal

/**
 * Local Room cache entity for notifications.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["timestamp"]),
        Index(value = ["isRead"]),
        Index(value = ["userId", "timestamp"]),
        Index(value = ["userId", "isRead"])
    ]
)
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val userId: String = "",
    val title: String,
    val message: String,
    val amount: BigDecimal?,
    val type: String,
    val timestamp: String,
    val reference: String?,
    val isRead: Boolean = false
) {
    fun toDomain(): Notification = Notification(
        id = id,
        title = title,
        message = message,
        amount = amount,
        type = type,
        timestamp = timestamp,
        reference = reference,
        isRead = isRead
    )

    companion object {
        fun fromDomain(notification: Notification, userId: String = ""): NotificationEntity = NotificationEntity(
            id = notification.id,
            userId = userId,
            title = notification.title,
            message = notification.message,
            amount = notification.amount,
            type = notification.type,
            timestamp = notification.timestamp,
            reference = notification.reference,
            isRead = notification.isRead
        )
    }
}
