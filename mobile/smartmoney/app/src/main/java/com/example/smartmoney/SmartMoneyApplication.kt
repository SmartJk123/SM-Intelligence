package com.example.smartmoney

import android.app.Application
import com.example.smartmoney.core.di.AppContainer
import com.example.smartmoney.core.di.DefaultAppContainer
import com.example.smartmoney.core.notification.NotificationHelper
import com.example.smartmoney.data.local.UserProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom [Application] class for SmartMoney, serving as the root dependency owner
 * and orchestrating application-level singletons via [AppContainer].
 */
class SmartMoneyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        NotificationHelper.createNotificationChannel(this)

        val initialUserId = container.authRepository.currentUserId()
        CoroutineScope(Dispatchers.IO).launch {
            if (!initialUserId.isNullOrBlank()) {
                UserProfileManager.switchUser(this@SmartMoneyApplication, initialUserId)
            } else {
                UserProfileManager.clearSession()
            }
        }
    }
}
