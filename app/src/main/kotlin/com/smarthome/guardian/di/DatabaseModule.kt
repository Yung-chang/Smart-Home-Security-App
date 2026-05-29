package com.smarthome.guardian.di

import android.content.Context
import androidx.room.Room
import com.smarthome.guardian.data.local.database.AppDatabase
import com.smarthome.guardian.data.local.database.DeviceDao
import com.smarthome.guardian.data.local.database.AlertDao
import com.smarthome.guardian.data.local.database.AuditDao
import com.smarthome.guardian.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "smarthome_guardian.db"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): AppDatabase {
        // SQLCipher passphrase is derived from the Android Keystore — never stored in plain text
        val passphrase = SQLiteDatabase.getBytes(keyProvider.getDatabasePassphrase())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            // Wipe and recreate on destructive migration rather than silently corrupting data
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()
}
