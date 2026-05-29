package com.smarthome.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.service.SecurityService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * 裝置重新開機後自動啟動 [SecurityService] 的 [BroadcastReceiver]。
 *
 * ## 觸發時機
 * - `android.intent.action.BOOT_COMPLETED`：裝置正常開機完成
 * - `android.intent.action.QUICKBOOT_POWERON`：部分廠商的快速開機事件（如 HTC）
 *
 * ## 啟動前置條件
 * 只有在使用者**曾經登入過**（[SecurePreferences] 中有 Refresh Token）的情況下才啟動 Service。
 * 尚未登入的裝置開機後不啟動守護服務，等待使用者主動登入後再由 [com.smarthome.guardian.MainActivity] 觸發。
 *
 * ## OWASP M1（不當平台使用）對應
 * - `android:exported="false"`：此廣播為系統廣播，已自動接收；`exported=false` 防止其他 APP 模擬觸發
 * - 不在 `onReceive` 中執行長時間操作（使用 `startForegroundService` 立即委派）
 *
 * ## AndroidManifest.xml 必要設定
 * ```xml
 * <receiver
 *     android:name=".receiver.BootReceiver"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED" />
 *         <action android:name="android.intent.action.QUICKBOOT_POWERON" />
 *     </intent-filter>
 * </receiver>
 * ```
 * 並需要在 Manifest 宣告 `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    /**
     * 用於判斷使用者是否已登入（有 Refresh Token）。
     * 避免未登入裝置開機後也啟動守護服務浪費資源。
     */
    @Inject lateinit var securePreferences: SecurePreferences

    /**
     * 接收開機廣播並決定是否啟動 [SecurityService]。
     *
     * ## 處理邏輯
     * 1. 驗證 Intent action 是否為開機廣播（防禦性檢查）
     * 2. 確認使用者已登入（[SecurePreferences] 中有 Refresh Token）
     * 3. 呼叫 [SecurityService.start] 以 Foreground Service 模式啟動守護服務
     *
     * `onReceive` 在主執行緒執行且時間有限（系統要求快速返回），
     * 實際的長時間操作（MQTT 連線、WebSocket 等）在 [SecurityService] 中以 Coroutine 執行。
     *
     * @param context 系統提供的 [Context]（用於啟動 Service）
     * @param intent  開機廣播的 [Intent]（action 為 `BOOT_COMPLETED` 或 `QUICKBOOT_POWERON`）
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        Timber.d("BootReceiver: received $action")

        // 只有已登入的用戶才啟動守護服務
        if (securePreferences.getRefreshToken().isNullOrBlank()) {
            Timber.d("BootReceiver: no session found, skipping SecurityService start")
            return
        }

        Timber.i("BootReceiver: session found, starting SecurityService")
        SecurityService.start(context)
    }
}
