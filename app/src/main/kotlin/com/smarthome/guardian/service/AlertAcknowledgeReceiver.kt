package com.smarthome.guardian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smarthome.guardian.data.logger.AuditLogger
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.repository.SecurityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 通知快捷操作「確認警報」的 [BroadcastReceiver]。
 *
 * 使用者在通知欄點擊「確認」按鈕時，系統發送 [ACTION_ACK_ALERT] 廣播，
 * 此 Receiver 接收後執行確認流程，**無需開啟 APP**。
 *
 * ## 執行流程
 * ```
 * 使用者點擊通知「確認」按鈕
 *   → 系統廣播 ACTION_ACK_ALERT（含 alert_id extra）
 *   → onReceive() 以 goAsync() 進入非同步模式
 *   → IO 執行緒：
 *       1. SecurityRepository.acknowledgeAlert(alertId) 更新本地 DB + 同步伺服器
 *       2. NotificationHelper.cancel(alertId.hashCode()) 移除通知
 *       3. AuditLogger.log() 寫入稽核日誌
 *   → pendingResult.finish() 通知系統非同步工作完成
 * ```
 *
 * ## goAsync() 的必要性
 * [BroadcastReceiver.onReceive] 預設在主執行緒執行且有嚴格的時間限制（~10 秒）。
 * 呼叫 [goAsync] 可延長此限制，讓協程在背景 IO 執行緒完成資料庫與網路操作。
 *
 * ## 廣播安全性（OWASP M1 — 不當平台使用）
 * - `android:exported="false"`：只有本 APP 能觸發此廣播（在 Manifest 宣告）
 * - [NotificationHelper.buildAckPendingIntent] 以 `setPackage(context.packageName)` 限制目標，
 *   防止其他 APP 攔截或偽造確認操作
 * - `PendingIntent.FLAG_IMMUTABLE`：防止 PendingIntent 的 extras 被修改
 *
 * ## AndroidManifest.xml 必要設定
 * ```xml
 * <receiver
 *     android:name=".service.AlertAcknowledgeReceiver"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.smarthome.guardian.ACK_ALERT" />
 *     </intent-filter>
 * </receiver>
 * ```
 */
@AndroidEntryPoint
class AlertAcknowledgeReceiver : BroadcastReceiver() {

    /** 警報 Repository：執行確認並同步至伺服器。 */
    @Inject lateinit var securityRepository: SecurityRepository

    /** 通知輔助：在確認後取消對應的系統通知。 */
    @Inject lateinit var notificationHelper: NotificationHelper

    /** 稽核日誌：記錄用戶透過通知快捷操作執行確認的事件。 */
    @Inject lateinit var auditLogger: AuditLogger

    /**
     * 背景 IO CoroutineScope。
     *
     * 使用 [SupervisorJob] 確保單一警報確認失敗不影響後續操作。
     * 生命週期與 Receiver 相同；由於 Receiver 是短生命週期元件，
     * 此 scope 不需要顯式取消（協程完成後自然釋放）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 接收廣播並執行警報確認流程。
     *
     * 只處理 [ACTION_ACK_ALERT] 廣播；其他 action 直接忽略（防禦性設計）。
     * 以 [goAsync] 延長處理時間，在 IO 執行緒完成非同步工作後呼叫 `pendingResult.finish()`。
     *
     * ## 防禦性驗證
     * - 若 `alert_id` extra 遺失或空白，記錄警告並提前返回（不拋出例外）
     * - 資料庫 / 網路操作以 [runCatching] 包覆，失敗時記錄錯誤但仍呼叫 `finish()`
     *
     * @param context Android 系統提供的 [Context]，用於 Hilt 注入初始化
     * @param intent  系統廣播的 [Intent]，應包含：
     *   - `action = ACTION_ACK_ALERT`
     *   - extra `alert_id`（String）= 要確認的警報 UUID
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACK_ALERT) return

        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        if (alertId.isNullOrBlank()) {
            Timber.w("AlertAcknowledgeReceiver: missing or blank $EXTRA_ALERT_ID extra")
            return
        }

        val pendingResult = goAsync()

        scope.launch {
            runCatching {
                // 1. 更新本地 DB + 非同步同步至伺服器（SecurityRepositoryImpl 實作）
                securityRepository.acknowledgeAlert(alertId)

                // 2. 從通知欄移除已確認的警報通知
                notificationHelper.cancel(alertId.hashCode())

                // 3. 寫入稽核日誌（記錄快捷確認操作，供後續稽核使用）
                auditLogger.log(
                    action   = AuditAction.ALERT_ACKNOWLEDGED,
                    targetId = alertId,
                    after    = mapOf("method" to "notification_quick_action"),
                )
                Timber.d("Alert acknowledged via quick action: alertId=$alertId")
            }.onFailure { e ->
                Timber.e(e, "Failed to acknowledge alert: alertId=$alertId")
            }.also {
                // 無論成功或失敗，都必須呼叫 finish() 通知系統非同步工作結束
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * 廣播 Action：通知快捷「確認」按鈕觸發的廣播動作字串。
         *
         * 對應 [NotificationHelper.buildAckPendingIntent] 中設定的 Intent action，
         * 以及 AndroidManifest.xml `<intent-filter>` 中的 `<action>` 宣告。
         */
        const val ACTION_ACK_ALERT = "com.smarthome.guardian.ACK_ALERT"

        /**
         * Intent Extra 鍵名：目標警報的 UUID 字串。
         *
         * 由 [NotificationHelper.buildAckPendingIntent] 設定，
         * 在 [onReceive] 中以 `intent.getStringExtra(EXTRA_ALERT_ID)` 取得。
         */
        const val EXTRA_ALERT_ID = "alert_id"
    }
}
