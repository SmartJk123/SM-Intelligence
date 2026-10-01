package com.example.smartmoney.domain.repository

import com.example.smartmoney.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(userId: String? = null): Flow<List<Notification>>
    fun getUnreadCount(userId: String? = null): Flow<Int>
    suspend fun insertNotification(notification: Notification, userId: String? = null)
    suspend fun markAsRead(id: String, userId: String? = null)
    suspend fun markAllAsRead(userId: String? = null)
    suspend fun deleteNotification(id: String, userId: String? = null)
    suspend fun clearAll(userId: String? = null)
}
