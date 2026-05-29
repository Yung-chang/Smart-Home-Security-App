package com.smarthome.guardian

import android.app.Application
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point. @HiltAndroidApp triggers Hilt code generation and
 * attaches the application-level DI component to the process lifecycle.
 *
 * Security hardening:
 *  - Timber logging only planted in debug builds; release tree is a no-op
 *  - StrictMode catches accidental disk/network access on the main thread
 */
@HiltAndroidApp
class SmartHomeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (isDebugBuild()) {
            Timber.plant(Timber.DebugTree())
            enableStrictMode()
        }
        // Release: no Timber tree planted → all Timber calls become no-ops
    }

    private fun isDebugBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun enableStrictMode() {
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}
