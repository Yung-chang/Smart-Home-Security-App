package com.smarthome.guardian.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smarthome.guardian.data.local.database.entity.AlertEntity
import com.smarthome.guardian.data.local.database.entity.AuditLogEntity
import com.smarthome.guardian.data.local.database.entity.DeviceEntity

/**
 * 應用程式主資料庫（SQLCipher AES-256 加密）。
 *
 * 透過 [com.smarthome.guardian.di.DatabaseModule] 由 Hilt 提供，
 * 使用 Android Keystore 衍生的 passphrase，金鑰不可匯出。
 *
 * 版本升級時透過 Migration 或 fallbackToDestructiveMigration 處理。
 */
@Database(
    entities = [
        DeviceEntity::class,
        AlertEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun alertDao(): AlertDao
    abstract fun auditDao(): AuditDao
}
