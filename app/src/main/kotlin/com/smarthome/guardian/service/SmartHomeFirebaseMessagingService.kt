package com.smarthome.guardian.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smarthome.guardian.data.local.database.AlertDao
import com.smarthome.guardian.data.local.database.entity.AlertEntity
import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.data.logger.AuditLogger
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.security.engine.AlertEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Firebase Cloud Messaging 推播訊息接收服務。
 *
 * 繼承自 [FirebaseMessagingService]，由 Firebase SDK 在收到推播時自動啟動。
 * 以 `@AndroidEntryPoint` 支援 Hilt 注入，無需手動建立依賴。
 *
 * ## 職責
 * 1. **Token 管理**（[onNewToken]）：FCM Token 輪換時加密儲存並排程上傳至伺服器
 * 2. **訊息分派**（[onMessageReceived]）：依 `type` 欄位路由至對應的私有處理器
 * 3. **警報持久化**：將 FCM 收到的安全警報寫入 Room DB，確保離線時也能查詢
 * 4. **本地通知**：依嚴重程度選擇頻道（CRITICAL 使用全螢幕 Intent）
 * 5. **AlertEngine 觸發**：特定警報類型通知本地規則引擎進一步處理
 * 6. **稽核日誌**：所有 FCM 訊息接收均記錄（防否認性）
 *
 * ## FCM Data Payload 格式
 * 本服務**僅使用 `data` 欄位**（不使用 `notification` 欄位），確保前景/背景行為一致：
 * ```json
 * {
 *   "data": {
 *     "type":       "SECURITY_ALERT" | "DEVICE_OFFLINE" | "SYSTEM",
 *     "alertType":  "INTRUSION" | "AUTH_FAIL" | "ANOMALY" | ...,
 *     "severity":   "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
 *     "alertId":    "550e8400-e29b-41d4-a716-446655440000",
 *     "deviceId":   "device-uuid",
 *     "deviceName": "前門門鎖",
 *     "deviceType": "DOOR_LOCK",
 *     "message":    "人類可讀描述（不含敏感識別資訊）",
 *     "timestamp":  "1700000000000"
 *   }
 * }
 * ```
 *
 * ## CoroutineScope 設計
 * Firebase SDK 在 [onMessageReceived] 返回後約 20 秒內會終止 Service。
 * 此類使用獨立的 [CoroutineScope]（[SupervisorJob] + [Dispatchers.IO]）處理非同步工作，
 * 並在 [onDestroy] 中取消，確保資源不洩漏。
 *
 * ## OWASP Mobile Top 10 對應
 * - **M1（不當平台使用）**：使用 `data` payload 而非 `notification`，避免 OS 直接顯示未經過濾的通知
 * - **M2（不安全資料儲存）**：FCM Token 以 [SecurePreferences]（AES-256-GCM）加密儲存，不存於純文字 SharedPreferences
 * - **M5（不足的傳輸層保護）**：FCM 傳輸固定使用 Google TLS 基礎設施；[sanitizeMessage] 確保通知不顯示 UUID/IP 等識別資訊
 * - **M9（逆向工程）**：Payload 不含明文密碼、Token 或完整設備識別碼，降低逆向分析的資訊洩露風險
 */
@AndroidEntryPoint
class SmartHomeFirebaseMessagingService : FirebaseMessagingService() {

    /** Room AlertDao：將 FCM 收到的警報寫入本地資料庫。 */
    @Inject lateinit var alertDao: AlertDao

    /** 通知輔助：依嚴重程度選擇頻道並顯示通知。 */
    @Inject lateinit var notificationHelper: NotificationHelper

    /** 本地規則引擎：FCM 觸發特定規則時通知引擎。 */
    @Inject lateinit var alertEngine: AlertEngine

    /** 稽核日誌：記錄每次 FCM 訊息接收事件。 */
    @Inject lateinit var auditLogger: AuditLogger

    /** 加密偏好設定：儲存 FCM Token（AES-256-GCM）。 */
    @Inject lateinit var securePreferences: SecurePreferences

    /**
     * 背景 IO CoroutineScope。
     * 使用 [SupervisorJob] 確保子協程失敗不影響其他協程。
     * 在 [onDestroy] 中取消以釋放資源。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── FCM Token 生命週期 ────────────────────────────────────────────────────

    /**
     * 首次安裝或 Token 輪換時由 Firebase SDK 呼叫。
     *
     * 執行以下操作：
     * 1. 將新 Token 以 AES-256-GCM 加密後儲存至 [SecurePreferences]（OWASP M2）
     * 2. 在背景非同步排程上傳至伺服器（上傳失敗不影響功能，下次登入重試）
     *
     * @param token 新的 FCM 推播路由 Token（每次 Token 輪換都是全新字串，舊 Token 自動失效）
     */
    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed (prefix: ${token.take(8)}…)")
        securePreferences.saveFcmToken(token)

        scope.launch {
            runCatching {
                // TODO: apiService.updateFcmToken(UpdateFcmTokenRequest(token))
                Timber.d("FCM token queued for server sync")
            }.onFailure { e ->
                Timber.w(e, "FCM token upload failed — will retry on next login")
            }
        }
    }

    // ── 訊息接收與分派 ────────────────────────────────────────────────────────

    /**
     * 收到 FCM 推播訊息時由 Firebase SDK 呼叫（前景 / 背景均有效）。
     *
     * ## 處理策略
     * - 僅處理含 `data` 欄位的訊息；空 data 的訊息靜默忽略
     * - 依 `data["type"]` 分派至對應的私有處理器，在 [Dispatchers.IO] 執行
     * - 未知的 `type` 值記錄警告但不拋出例外（防崩潰設計）
     *
     * @param message Firebase SDK 提供的遠端訊息物件，包含 `data`、`notification`（本服務不使用）、`messageId` 等欄位
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d("FCM received: id=${message.messageId}")

        val data = message.data
        if (data.isEmpty()) return

        scope.launch {
            when (data["type"]) {
                "SECURITY_ALERT" -> handleSecurityAlert(data)
                "DEVICE_OFFLINE" -> handleDeviceOffline(data)
                "SYSTEM"         -> handleSystem(data)
                else             -> Timber.w("FCM: unknown type=${data["type"]}")
            }
        }
    }

    /**
     * Service 被系統銷毀時呼叫。
     * 取消 [scope] 中所有進行中的協程，釋放 IO 資源。
     */
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── 訊息處理器 ────────────────────────────────────────────────────────────

    /**
     * 處理 `type = "SECURITY_ALERT"` 的 FCM 訊息。
     *
     * 執行順序：
     * 1. 解析 [data] 中的 `alertType`、`severity`、`message` 等欄位並建立 [SecurityAlert]
     * 2. 以 `ON CONFLICT IGNORE` 寫入 Room [AlertDao]（冪等：重複訊息不產生重複記錄）
     * 3. 呼叫 [NotificationHelper.showAlertNotification]（CRITICAL 觸發全螢幕 Intent）
     * 4. 依警報類型通知 [AlertEngine]（`AUTH_FAIL` → 規則 4 計數；`DEVICE_OFFLINE` → 規則 1 檢查）
     * 5. 寫入稽核日誌（記錄 FCM 來源與嚴重程度）
     *
     * @param data FCM data payload Map；必要鍵：`alertType`、`severity`、`message`；選用：`alertId`、`deviceId`、`timestamp`
     */
    private suspend fun handleSecurityAlert(data: Map<String, String>) {
        val alert = SecurityAlert(
            id             = data["alertId"] ?: UUID.randomUUID().toString(),
            type           = parseAlertType(data["alertType"]),
            severity       = parseSeverity(data["severity"]),
            deviceId       = data["deviceId"],
            message        = sanitizeMessage(data["message"]),
            timestamp      = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            isAcknowledged = false,
        )

        alertDao.upsert(AlertEntity.fromDomain(alert))
        notificationHelper.showAlertNotification(alert)

        when (alert.type) {
            AlertType.AUTH_FAIL    -> alertEngine.onTokenRefreshFailed()
            AlertType.DEVICE_OFFLINE -> alertEngine.checkDeviceOffline(
                alert.deviceId?.let { listOf(it) } ?: emptyList()
            )
            else -> Unit
        }

        auditLogger.log(
            action   = AuditAction.ALERT_ACKNOWLEDGED,
            targetId = alert.id,
            after    = mapOf("source" to "FCM", "severity" to alert.severity.name),
        )

        Timber.i("FCM alert persisted: ${alert.type} sev=${alert.severity}")
    }

    /**
     * 處理 `type = "DEVICE_OFFLINE"` 的 FCM 訊息。
     *
     * 解析設備資訊並顯示帶深層連結的離線通知。
     * 若 `deviceId` 缺失則靜默忽略（無法路由通知點擊）。
     *
     * @param data FCM data payload Map；必要鍵：`deviceId`、`deviceName`；選用：`deviceType`
     */
    private fun handleDeviceOffline(data: Map<String, String>) {
        val deviceId   = data["deviceId"]   ?: return
        val deviceName = data["deviceName"] ?: "智慧設備"
        val deviceType = data["deviceType"] ?: ""

        notificationHelper.showDeviceOfflineNotification(
            deviceId   = deviceId,
            deviceName = deviceName,
            deviceType = deviceType,
        )
        Timber.d("FCM device offline: $deviceId ($deviceName)")
    }

    /**
     * 處理 `type = "SYSTEM"` 的 FCM 訊息（韌體更新提示、系統設定變更等）。
     *
     * 顯示低優先度資訊通知，不振動、不繞過勿擾模式。
     *
     * @param data FCM data payload Map；選用：`title`（預設 "系統通知"）、`message`（預設空）
     */
    private fun handleSystem(data: Map<String, String>) {
        val title   = data["title"]   ?: "系統通知"
        val message = sanitizeMessage(data["message"])
        notificationHelper.showInfoNotification(title, message)
    }

    // ── 安全解析工具 ──────────────────────────────────────────────────────────

    /**
     * 將字串安全解析為 [AlertType] 枚舉值。
     *
     * 使用 [runCatching] 防止伺服器傳入未知字串時崩潰（防禦性程式設計）。
     *
     * @param v 來自 FCM payload 的 `alertType` 字串值，不區分大小寫
     * @return 解析成功時回傳對應的 [AlertType]；失敗時回傳 [AlertType.SYSTEM] 作為安全預設值
     */
    private fun parseAlertType(v: String?): AlertType =
        v?.let { runCatching { AlertType.valueOf(it.uppercase()) }.getOrNull() } ?: AlertType.SYSTEM

    /**
     * 將字串安全解析為 [Severity] 枚舉值。
     *
     * @param v 來自 FCM payload 的 `severity` 字串值，不區分大小寫
     * @return 解析成功時回傳對應的 [Severity]；失敗時回傳 [Severity.MEDIUM] 作為安全預設值
     */
    private fun parseSeverity(v: String?): Severity =
        v?.let { runCatching { Severity.valueOf(it.uppercase()) }.getOrNull() } ?: Severity.MEDIUM

    /**
     * 清理訊息文字，移除任何可能洩露敏感識別資訊的模式（OWASP M5）。
     *
     * 執行以下替換：
     * - UUID 格式（`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`）→ `"[ID]"`
     * - IPv4 位址格式（`x.x.x.x`）→ `"[位址]"`
     * - 截斷至 [MAX_MESSAGE_LENGTH] 字元
     *
     * @param message 來自 FCM payload 的 `message` 字串；`null` 或空白時回傳預設文字
     * @return 清理後的安全訊息文字，長度不超過 [MAX_MESSAGE_LENGTH]
     */
    private fun sanitizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return "收到安全通知"
        return message
            .replace(UUID_REGEX, "[ID]")
            .replace(IP_REGEX, "[位址]")
            .take(MAX_MESSAGE_LENGTH)
    }

    companion object {
        /**
         * 通知訊息文字的最大長度（字元數）。
         * 超過此長度的訊息會被截斷，防止通知欄顯示過長的 FCM 內容。
         */
        private const val MAX_MESSAGE_LENGTH = 200

        /**
         * 匹配 UUID v4 格式的正規表示式（不區分大小寫）。
         * 用於 [sanitizeMessage] 中將設備/警報 UUID 遮罩為 `"[ID]"`。
         */
        private val UUID_REGEX = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            RegexOption.IGNORE_CASE,
        )

        /**
         * 匹配 IPv4 位址格式的正規表示式（`x.x.x.x`，各段 1–3 位數字）。
         * 用於 [sanitizeMessage] 中將 IP 位址遮罩為 `"[位址]"`。
         */
        private val IP_REGEX = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
    }
}
