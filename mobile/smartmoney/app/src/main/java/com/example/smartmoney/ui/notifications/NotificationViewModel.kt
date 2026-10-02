package com.example.smartmoney.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.core.coroutine.DefaultDispatcherProvider
import com.example.smartmoney.core.coroutine.DispatcherProvider
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.domain.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val userId: String? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val seenNotificationIds = mutableSetOf<String>()
    private var isInitialLoad = true
    private var bannerDismissJob: Job? = null

    private val _activeBanner = MutableStateFlow<Notification?>(null)
    val activeBanner: StateFlow<Notification?> = _activeBanner.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL") // "ALL" or "UNREAD"
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    init {
        // Observe incoming notifications to trigger in-app banners for new events
        viewModelScope.launch(dispatchers.default) {
            repository.getNotifications(userId = userId).collect { notifList ->
                if (isInitialLoad) {
                    // Populate initial historical IDs without popping up banners
                    seenNotificationIds.addAll(notifList.map { it.id })
                    isInitialLoad = false
                } else {
                    // Check for newly arrived notifications
                    val newNotifications = notifList.filter { it.id !in seenNotificationIds }
                    if (newNotifications.isNotEmpty()) {
                        seenNotificationIds.addAll(newNotifications.map { it.id })
                        // Show banner for the most recent new notification
                        val latest = newNotifications.first()
                        showBanner(latest)
                    }
                }
            }
        }
    }

    val unreadCount: StateFlow<Int> = repository.getUnreadCount(userId = userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val notifications: StateFlow<List<Notification>> = combine(
        repository.getNotifications(userId = userId),
        _selectedFilter
    ) { list, filter ->
        when (filter) {
            "UNREAD" -> list.filter { !it.isRead }
            else -> list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun markAsRead(id: String) {
        viewModelScope.launch(dispatchers.io) {
            repository.markAsRead(id, userId = userId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch(dispatchers.io) {
            repository.markAllAsRead(userId = userId)
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch(dispatchers.io) {
            repository.deleteNotification(id, userId = userId)
        }
    }

    fun clearAll() {
        viewModelScope.launch(dispatchers.io) {
            repository.clearAll(userId = userId)
        }
    }

    /**
     * Triggers an in-app banner for [notification] with automatic dismissal after [durationMillis].
     */
    fun showBanner(notification: Notification, durationMillis: Long = 4000L) {
        bannerDismissJob?.cancel()
        _activeBanner.value = notification
        bannerDismissJob = viewModelScope.launch(dispatchers.default) {
            delay(durationMillis)
            _activeBanner.value = null
        }
    }

    /**
     * Manually dismisses any active in-app notification banner.
     */
    fun dismissBanner() {
        bannerDismissJob?.cancel()
        _activeBanner.value = null
    }

    class Factory(
        private val repository: NotificationRepository,
        private val userId: String? = null,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationViewModel(repository, userId, dispatchers) as T
        }
    }
}
