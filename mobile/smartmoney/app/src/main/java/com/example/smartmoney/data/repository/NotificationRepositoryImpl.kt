package com.example.smartmoney.data.repository

import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.data.local.dao.NotificationDao
import com.example.smartmoney.data.local.entity.NotificationEntity
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationRepositoryImpl(
    private val notificationDao: NotificationDao,
    private val userIdProvider: (() -> String?)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : NotificationRepository {

    override fun getNotifications(userId: String?): Flow<List<Notification>> {
        val targetUserId = userId ?: userIdProvider?.invoke()
        val flow = if (!targetUserId.isNullOrBlank()) {
            notificationDao.getNotificationsForUser(targetUserId)
        } else {
            notificationDao.getAllNotifications()
        }
        return flow.map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.default)
    }

    override fun getUnreadCount(userId: String?): Flow<Int> {
        val targetUserId = userId ?: userIdProvider?.invoke()
        val flow = if (!targetUserId.isNullOrBlank()) {
            notificationDao.getUnreadCount(targetUserId)
        } else {
            notificationDao.getUnreadCount()
        }
        return flow.flowOn(dispatchers.default)
    }

    override suspend fun insertNotification(notification: Notification, userId: String?) = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke() ?: ""
        notificationDao.insertNotification(NotificationEntity.fromDomain(notification, targetUserId))
    }

    override suspend fun markAsRead(id: String, userId: String?) = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (!targetUserId.isNullOrBlank()) {
            notificationDao.markAsReadForUser(id, targetUserId)
        } else {
            notificationDao.markAsRead(id)
        }
    }

    override suspend fun markAllAsRead(userId: String?) = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (!targetUserId.isNullOrBlank()) {
            notificationDao.markAllAsReadForUser(targetUserId)
        } else {
            notificationDao.markAllAsRead()
        }
    }

    override suspend fun deleteNotification(id: String, userId: String?) = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (!targetUserId.isNullOrBlank()) {
            notificationDao.deleteNotificationForUser(id, targetUserId)
        } else {
            notificationDao.deleteNotification(id)
        }
    }

    override suspend fun clearAll(userId: String?) = withContext(dispatchers.io) {
        val targetUserId = userId ?: userIdProvider?.invoke()
        if (!targetUserId.isNullOrBlank()) {
            notificationDao.clearForUser(targetUserId)
        } else {
            notificationDao.clearAll()
        }
    }
}
