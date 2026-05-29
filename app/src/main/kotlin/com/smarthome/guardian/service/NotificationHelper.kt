package com.smarthome.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smarthome.guardian.MainActivity
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知管理輔助類別，集中管理 APP 所有通知頻道的建立與各類型通知的發送。
 *
 * 以 Singleton 提供，由 Hilt 注入至 [SecurityService]、[SmartHomeFirebaseMessagingService] 等元件。
 *
 * ## 通知頻道設計
 * | 頻道 ID            | 名稱         | 優先度 | 鎖定畫面    | 繞過勿擾 | 用途                      |
 * |------------------|------------|------|-----------|--------|---------------------------|
 * | [CHANNEL_CRITICAL] | 緊急安全警報 | MAX  | PUBLIC    | ✓     | 入侵偵測等 CRITICAL 警報    |
 * | [CHANNEL_SECURITY] | 安全警報     | HIGH | PRIVATE   | ✗     | HIGH / MEDIUM 等級警報     |
 * | [CHANNEL_DEVICE]   | 設備通知     | DEFAULT | PRIVATE | ✗   | 設備離線 / 狀態變更         |
 * | [CHANNEL_INFO]     | 資訊通知     | LOW  | PRIVATE   | ✗     | 系統訊息 / 韌體更新          |
 * | [CHANNEL_SERVICE]  | 背景服務     | MIN  | SECRET    | ✗     | Foreground Service 持續通知 |
 *
 * ## 深層連結策略
 * - 警報通知點擊 → `navigate_to = "security"` + `alert_id` → [SecurityMonitorScreen]
 * - 設備離線通知點擊 → `navigate_to = "device"` + `device_id` → [DeviceDetailScreen]
 *
 * ## OWASP Mobile Top 10 對應
 * - **M1（不當平台使用）**：[PendingIntent.FLAG_IMMUTABLE] 防 PendingIntent 被第三方修改；
 *   [buildAckPendingIntent] 以 `setPackage()` 限制廣播只有本 APP 能接收
 * - **M2（不安全資料儲存）**：通知內容不含設備 ID、使用者 Email、完整地址等敏感識別資訊；
 *   背景服務通知使用 [Notification.VISIBILITY_SECRET] 隱藏鎖定畫面內容
 * - **M5（不足的傳輸層保護）**：深層連結 Intent extras 僅含預定義路由字串，不含內部 API Token
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        // ── 頻道 ID 常數 ──────────────────────────────────────────────────────

        /** CRITICAL 安全警報頻道 ID（最高優先度，繞過勿擾模式，鎖定畫面 PUBLIC）。 */
        const val CHANNEL_CRITICAL = "channel_critical"

        /** 高優先度安全警報頻道 ID（HIGH / MEDIUM 等級，鎖定畫面 PRIVATE）。 */
        const val CHANNEL_SECURITY = "channel_security"

        /** 預設優先度設備通知頻道 ID（設備離線、韌體更新等）。 */
        const val CHANNEL_DEVICE   = "channel_device"

        /** 低優先度資訊通知頻道 ID（系統訊息、設定變更等）。 */
        const val CHANNEL_INFO     = "channel_info"

        /** 最低優先度背景服務持續通知頻道 ID（Foreground Service 用）。 */
        const val CHANNEL_SERVICE  = "channel_service"

        // ── 通知 ID 常數 ──────────────────────────────────────────────────────

        /** Foreground Service 持續通知的固定通知 ID。 */
        const val NOTIFICATION_ID_SERVICE = 1001

        // ── Intent Extra 鍵名 ─────────────────────────────────────────────────

        /** Intent extra 鍵名：導航目標路由（值為 `"security"` / `"device"` / `"audit"`）。 */
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        /** Intent extra 鍵名：目標警報的 UUID 字串。 */
        const val EXTRA_ALERT_ID    = "alert_id"

        /** Intent extra 鍵名：目標設備的 UUID 字串。 */
        const val EXTRA_DEVICE_ID   = "device_id"
    }

    init {
        createChannels()
    }

    // ── 安全警報通知 ──────────────────────────────────────────────────────────

    /**
     * 發送安全警報通知，依嚴重程度選擇通知頻道與顯示方式。
     *
     * ## 行為差異（依 [SecurityAlert.severity]）
     * | 等級       | 頻道             | 全螢幕 Intent | 鎖定畫面可見性 | 優先度 |
     * |-----------|----------------|-------------|------------|------|
     * | CRITICAL  | [CHANNEL_CRITICAL] | ✓ 全螢幕    | PUBLIC     | MAX  |
     * | 其他等級   | [CHANNEL_SECURITY] | ✗ 一般      | PRIVATE    | HIGH |
     *
     * 所有警報通知均附帶「確認」快捷操作按鈕，不需開啟 APP 即可確認警報。
     *
     * @param alert  要顯示的安全警報資料，包含 `type`、`severity`、`message`、`id` 等欄位
     * @param device 觸發警報的設備（可為 `null`）；**僅顯示設備類型（如「智慧門鎖」），不顯示設備名稱或 ID**，
     *               以保護家庭配置隱私（OWASP M2）
     */
    fun showAlertNotification(alert: SecurityAlert, device: Device? = null) {
        val isCritical = alert.severity == Severity.CRITICAL
        val channelId  = if (isCritical) CHANNEL_CRITICAL else CHANNEL_SECURITY

        val locationHint = device?.type?.displayName?.let { "（$it）" } ?: ""
        val bodyText     = "${alert.message}$locationHint"

        val tapIntent = buildSecurityTapIntent(alert.id)
        val ackIntent = buildAckPendingIntent(alert.id)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(alert.type.displayName)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isCritical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(
                if (isCritical) NotificationCompat.VISIBILITY_PUBLIC
                else            NotificationCompat.VISIBILITY_PRIVATE
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "確認",
                ackIntent,
            )

        if (isCritical) {
            builder.setFullScreenIntent(tapIntent, /* highPriority = */ true)
        }

        showNotification(alert.id.hashCode(), builder.build())
        Timber.d("Alert notification shown: ${alert.severity} id=${alert.id}")
    }

    // ── 設備離線通知 ──────────────────────────────────────────────────────────

    /**
     * 發送設備離線通知（[Device] 物件版本）。
     *
     * 將 [Device] 轉換後委派至字串版本。
     * **僅使用設備類型顯示名稱（[Device.type.displayName]），不使用設備自訂名稱**，
     * 以防通知內容洩露過於具體的家庭設備配置資訊（OWASP M2）。
     *
     * @param device 已離線的設備物件；其 `id` 用於深層連結，`type.displayName` 用於通知文字
     */
    fun showDeviceOfflineNotification(device: Device) {
        showDeviceOfflineNotification(
            deviceId   = device.id,
            deviceName = device.type.displayName,
            deviceType = device.type.name,
        )
    }

    /**
     * 發送設備離線通知（字串版本，供 [SmartHomeFirebaseMessagingService] 使用）。
     *
     * 通知點擊後透過 [EXTRA_NAVIGATE_TO]（值 `"device"`）和 [EXTRA_DEVICE_ID]
     * 深層連結至 [DeviceDetailScreen]。
     *
     * @param deviceId   設備的 UUID 字串，用於：(1) 通知 ID、(2) 深層連結 extra
     * @param deviceName 設備顯示名稱（建議使用設備類型名稱，避免顯示使用者自訂的具體名稱）
     * @param deviceType 設備類型字串（目前未用於通知文字，保留供未來擴充使用）
     */
    fun showDeviceOfflineNotification(
        deviceId: String,
        deviceName: String,
        deviceType: String,
    ) {
        val tapIntent = PendingIntent.getActivity(
            context, deviceId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, "device")
                putExtra(EXTRA_DEVICE_ID,   deviceId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val bodyText = "$deviceName 已失去連線，請確認電源與網路狀態"
        val notification = NotificationCompat.Builder(context, CHANNEL_DEVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("設備離線")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\n點擊查看設備詳情"))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        showNotification(deviceId.hashCode(), notification)
    }

    // ── 資訊通知 ──────────────────────────────────────────────────────────────

    /**
     * 發送低優先度資訊通知（系統訊息、韌體更新提示等）。
     *
     * 使用 [CHANNEL_INFO] 頻道，**不振動、不繞過勿擾模式、不顯示於 App Badge**，
     * 適合非緊急的系統狀態更新。
     *
     * @param title   通知標題（建議 25 字元以內）
     * @param message 通知內文，以 [NotificationCompat.BigTextStyle] 顯示完整內容
     */
    fun showInfoNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        // 使用時間戳作為通知 ID，確保多筆資訊通知互不覆蓋
        showNotification(System.currentTimeMillis().toInt(), notification)
    }

    // ── Foreground Service 通知 ────────────────────────────────────────────────

    /**
     * 建立 Foreground Service 的持續通知物件。
     *
     * 此通知由 [SecurityService] 在 `onCreate()` 中透過 `startForeground()` 顯示，
     * 並在狀態變更時透過 [updateServiceNotification] 更新文字。
     *
     * ## 設計要點
     * - 使用 [Notification.VISIBILITY_SECRET]：鎖定畫面不顯示任何內容，防止旁觀者知道 APP 運行狀態
     * - `setOngoing(true)`：通知不可被滑動刪除（Foreground Service 限制）
     * - `PRIORITY_MIN`：在通知欄摺疊顯示，不打擾使用者
     * - 點擊通知跳轉至 [MainActivity]（Dashboard）
     *
     * @param status 顯示於通知內文的狀態描述（如 `"保護中"`、`"偵測到 HIGH 警報"` 等）
     * @return 配置完整的 [Notification] 物件，供 `startForeground()` 使用
     */
    fun buildServiceNotification(status: String = "保護中"): Notification {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("SmartHome Guardian")
            .setContentText("正在$status")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    /**
     * 更新已顯示的 Foreground Service 通知文字。
     *
     * 在 [SecurityService.onStartCommand] 及警報狀態變更時呼叫，
     * 讓使用者透過通知欄即時了解 APP 的運行狀態。
     *
     * @param status 新的狀態描述文字（如 `"保護中"`、`"偵測到 CRITICAL 警報"`）
     */
    fun updateServiceNotification(status: String) {
        manager.notify(NOTIFICATION_ID_SERVICE, buildServiceNotification(status))
    }

    /**
     * 取消（移除）指定 ID 的通知。
     *
     * 主要用途：[AlertAcknowledgeReceiver] 在確認警報後呼叫，
     * 移除通知欄中對應的警報通知。
     *
     * @param notificationId 要取消的通知 ID（通常為 `alertId.hashCode()` 或 `deviceId.hashCode()`）
     */
    fun cancel(notificationId: Int) = manager.cancel(notificationId)

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /**
     * 建立所有通知頻道（Android 8 Oreo+ 必要步驟）。
     *
     * 在 `init` 區塊呼叫，確保 APP 啟動時頻道已存在。
     * `createNotificationChannel` 為冪等操作：重複呼叫相同 ID 的頻道不會覆蓋使用者自訂的設定。
     */
    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_CRITICAL, "緊急安全警報", NotificationManager.IMPORTANCE_HIGH).apply {
                description          = "入侵偵測等緊急事件，保持最高警覺"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)   // 繞過勿擾模式（需 ACCESS_NOTIFICATION_POLICY 權限）
            },
            NotificationChannel(CHANNEL_SECURITY, "安全警報", NotificationManager.IMPORTANCE_HIGH).apply {
                description          = "設備異常、操作失敗等安全事件"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_DEVICE, "設備通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description          = "設備離線、韌體更新等一般通知"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_INFO, "資訊通知", NotificationManager.IMPORTANCE_LOW).apply {
                description          = "系統訊息、設定變更等資訊性通知"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_SERVICE, "背景服務", NotificationManager.IMPORTANCE_MIN).apply {
                description          = "SmartHome Guardian 正在背景保護您的家庭"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            },
        )
        channels.forEach { manager.createNotificationChannel(it) }
        Timber.d("Notification channels created: ${channels.map { it.id }}")
    }

    /**
     * 實際呼叫系統 [NotificationManagerCompat.notify] 發送通知。
     *
     * 以 [runCatching] 包覆：Android 13+ 未授予 `POST_NOTIFICATIONS` 權限時，
     * `notify()` 會拋出 [SecurityException]，靜默忽略並記錄警告（優雅降級，OWASP M1）。
     *
     * @param id           通知 ID（相同 ID 的通知會互相覆蓋）
     * @param notification 已建立的通知物件
     */
    private fun showNotification(id: Int, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.onFailure { e ->
            Timber.w(e, "Notification id=$id not shown (POST_NOTIFICATIONS permission denied?)")
        }
    }

    /**
     * 建立警報通知的點擊 [PendingIntent]，帶 deep link extra 跳轉至 [SecurityMonitorScreen]。
     *
     * 使用 [PendingIntent.FLAG_IMMUTABLE] 防止 PendingIntent 的 extras 被第三方 APP 修改（OWASP M1）。
     * `FLAG_ACTIVITY_SINGLE_TOP` 確保不重複建立 Activity 實例（[MainActivity] 是 Single-Activity 架構）。
     *
     * @param alertId 目標警報的 UUID 字串（作為 PendingIntent request code 的雜湊值，確保不同警報有不同 PendingIntent）
     * @return 配置完整的 [PendingIntent]，由通知的 `setContentIntent` 或 `setFullScreenIntent` 使用
     */
    private fun buildSecurityTapIntent(alertId: String): PendingIntent =
        PendingIntent.getActivity(
            context, alertId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, "security")
                putExtra(EXTRA_ALERT_ID,    alertId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 建立通知快捷「確認」按鈕的廣播 [PendingIntent]。
     *
     * 觸發 [AlertAcknowledgeReceiver] 執行確認流程，無需開啟 APP。
     * 以 `setPackage(context.packageName)` 限制廣播目標，
     * 確保只有本 APP 能接收此廣播，防止惡意 APP 偽造確認操作（OWASP M1）。
     *
     * @param alertId 目標警報的 UUID 字串（作為 PendingIntent request code 的雜湊值）
     * @return 配置完整的廣播 [PendingIntent]，由通知的 `addAction()` 使用
     */
    private fun buildAckPendingIntent(alertId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, alertId.hashCode(),
            Intent(AlertAcknowledgeReceiver.ACTION_ACK_ALERT).apply {
                setPackage(context.packageName)
                putExtra(AlertAcknowledgeReceiver.EXTRA_ALERT_ID, alertId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
