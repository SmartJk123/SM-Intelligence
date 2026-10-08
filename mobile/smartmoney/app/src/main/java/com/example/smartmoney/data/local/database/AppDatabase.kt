package com.example.smartmoney.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.smartmoney.data.local.dao.AccountConnectionDao
import com.example.smartmoney.data.local.dao.AccountDao
import com.example.smartmoney.data.local.dao.NotificationDao
import com.example.smartmoney.data.local.dao.TransactionDao
import com.example.smartmoney.data.local.dao.UserDao
import com.example.smartmoney.data.local.entity.AccountConnectionEntity
import com.example.smartmoney.data.local.entity.AccountEntity
import com.example.smartmoney.data.local.entity.NotificationEntity
import com.example.smartmoney.data.local.entity.TransactionEntity
import com.example.smartmoney.data.local.entity.UserEntity

/**
 * Local Room cache database.
 * Serves purely as an offline-first cache layer; Supabase remains the single source of truth.
 */
@Database(
    entities = [
        UserEntity::class,
        AccountEntity::class,
        AccountConnectionEntity::class,
        TransactionEntity::class,
        NotificationEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun accountConnectionDao(): AccountConnectionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "energy_cache_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
