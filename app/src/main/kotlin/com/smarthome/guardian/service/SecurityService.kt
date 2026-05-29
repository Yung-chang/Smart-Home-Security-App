package com.smarthome.guardian.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.data.remote.mqtt.MqttManager
import com.smarthome.guardian.data.remote.websocket.WebSocketManager
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.domain.repository.SecurityRepository
import com.smarthome.guardian.security.TokenManager
import com.smarthome.guardian.security.engine.AlertEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

/**
 * SmartHome Guardian 安全守護的 Foreground Service。
 *
 * 以 `START_STICKY` 模式運行，系統資源不足被終止後會自動重啟，
 * 確保 24/7 安全監控不中斷。以 `@AndroidEntryPoint` 支援 Hilt 注入。
 *
 * ## 職責（依重要性排序）
 * 1. **持續通知**：透過 [NotificationHelper.buildServiceNotification] 顯示「SmartHome Guardian 保護中」
 * 2. **MQTT 連線**：[MqttManager] 管理 TLS port 8883 連線（Client Certificate mTLS）
 * 3. **WebSocket 連線**：[WebSocketManager] 管理 WSS 連線（指數退避自動重連）
 * 4. **設備心跳**：訂閱 [DeviceRepository.getDevices]，將 ONLINE 設備回報至 [AlertEngine]
 * 5. **警報通知**：訂閱 [AlertEngine.alertFlow]，將本地觸發的警報顯示為通知
 * 6. **離線檢查**：每 [CHECK_INTERVAL_MS] 毫秒呼叫 [AlertEngine.checkDeviceOffline]（規則 1）
 *
 * ## 生命週期
 * ```
 * onCreate() → startForeground() → 顯示初始通知
 *   ↓
 * onStartCommand() → 啟動所有協程（網路連線、訂閱、定時任務）
 *   ↓
 * onDestroy() → 取消協程 → 斷開 MQTT/WebSocket
 * ```
 *
 * ## 並行設計
 * 所有長期運行任務在 [serviceScope]（[SupervisorJob] + [Dispatchers.IO]）中並行執行。
 * [SupervisorJob] 確保單一子協程失敗（如 MQTT 連線斷開）不會取消其他子協程（如 WebSocket 訂閱）。
 *
 * ## OWASP Mobile Top 10 對應
 * - **M3（不安全的通訊）**：MQTT 使用 TLS（`ssl://`）port 8883 + Android Keystore Client Certificate（mTLS）
 * - **M5（不足的傳輸層保護）**：WebSocket 使用 WSS（`wss://`）；兩者均由 [CertificatePinner] 驗證憑證
 * - **M6（不安全的授權）**：WebSocket 使用 JWT Access Token 認證，MQTT 使用 Android Keystore 憑證認證
 */
@AndroidEntryPoint
class SecurityService : Service() {

    /** 本地規則引擎：接收設備心跳、門鎖事件、Token 失敗事件，觸發對應的安全規則。 */
    @Inject lateinit var alertEngine: AlertEngine

    /** 設備 Repository：訂閱設備狀態流，用於設備心跳回報與離線檢查。 */
    @Inject lateinit var deviceRepository: DeviceRepository

    /** 安全警報 Repository：供未來擴充（如推送確認至 AlertEngine）使用。 */
    @Inject lateinit var securityRepository: SecurityRepository

    /** 通知輔助：顯示持續通知、警報通知，更新通知文字。 */
    @Inject lateinit var notificationHelper: NotificationHelper

    /** MQTT 管理器：管理與 Home Gateway 的 MQTT over TLS 連線。 */
    @Inject lateinit var mqttManager: MqttManager

    /** WebSocket 管理器：管理即時設備狀態與安全警報的 WSS 連線。 */
    @Inject lateinit var webSocketManager: WebSocketManager

    /** Token 管理器：取得 Access Token 用於 WebSocket 連線認證。 */
    @Inject lateinit var tokenManager: TokenManager

    /**
     * 所有長期協程的執行範圍。
     *
     * 使用 [SupervisorJob] 確保子協程間互相隔離：
     * 一個協程失敗（如 MQTT 連線斷開）不會取消其他協程（如 WebSocket 訂閱）。
     * 在 [onDestroy] 中以 `cancel()` 一次性終止所有子協程。
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Android Service 生命週期 ───────────────────────────────────────────────

    /**
     * Service 建立時呼叫（於 `startForegroundService()` 後執行）。
     *
     * 立即呼叫 `startForeground()` 防止 Android ANR：
     * 5 秒內未呼叫 `startForeground()` 會觸發系統異常並終止 Service。
     * 初始通知文字為「初始化中…」，[onStartCommand] 執行後更新為「保護中」。
     */
    override fun onCreate() {
        super.onCreate()
        Timber.d("SecurityService created")
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notificationHelper.buildServiceNotification("初始化中…"),
        )
    }

    /**
     * 每次 [start] 被呼叫時執行（包括系統自動重啟後的第一次呼叫）。
     *
     * 啟動所有背景協程：
     * - [connectNetworking]：建立 MQTT 與 WebSocket 連線
     * - [observeDeviceHeartbeats]：訂閱設備狀態流
     * - [observeAlertEngine]：訂閱本地規則觸發的警報
     * - [scheduleOfflineCheck]：每 5 分鐘執行設備離線檢查
     *
     * @param intent  啟動 Service 的 Intent（通常為空）
     * @param flags   啟動標記（`START_FLAG_REDELIVERY` 或 `START_FLAG_RETRY`）
     * @param startId 此次啟動的 ID（可用於 `stopSelf(startId)` 在任務完成後自行終止）
     * @return [Service.START_STICKY]：Service 被終止後系統會自動重啟（不傳遞原始 Intent）
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("SecurityService onStartCommand")
        notificationHelper.updateServiceNotification("保護中")

        serviceScope.launch {
            launch { connectNetworking() }
            launch { observeDeviceHeartbeats() }
            launch { observeAlertEngine() }
            launch { scheduleOfflineCheck() }
        }

        return START_STICKY
    }

    /**
     * Service 被銷毀時呼叫（系統主動終止或 [stop] 後）。
     *
     * 依序執行清理：
     * 1. 取消所有 [serviceScope] 中的協程（含網路訂閱、定時任務）
     * 2. 呼叫 [MqttManager.disconnect] 主動斷開 MQTT 連線
     * 3. 呼叫 [WebSocketManager.disconnect] 主動斷開 WebSocket 連線
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mqttManager.disconnect()
        webSocketManager.disconnect()
        Timber.d("SecurityService destroyed")
    }

    /**
     * 此 Service 不支援繫結（Bound Service）模式，固定回傳 `null`。
     *
     * @param intent 繫結請求的 Intent（不使用）
     * @return 永遠回傳 `null`，表示不提供 IBinder 介面
     */
    override fun onBind(intent: Intent?): IBinder? = null

    // ── 私有協程任務 ──────────────────────────────────────────────────────────

    /**
     * 建立 MQTT 與 WebSocket 的網路連線。
     *
     * 兩個連線並行發起，任一連線失敗時由各自的管理器（[MqttManager] / [WebSocketManager]）
     * 透過指數退避策略自動重連，此方法本身不進行重試。
     *
     * ## MQTT 連線
     * - Broker URL 來自 `BuildConfig.MQTT_BROKER_URL`（`ssl://host:8883` 格式）
     * - Client ID 包含 `Build.ID` 確保每台裝置唯一
     * - [MqttManager] 使用 Android Keystore 憑證建立 mTLS 雙向認證
     *
     * ## WebSocket 連線
     * - URL 來自 `BuildConfig.WS_BASE_URL`（`wss://host/ws` 格式）
     * - 使用 JWT Access Token 作為認證憑證（附加至 Query String）
     * - [WebSocketManager] 在連線成功後開始每 30 秒的應用層心跳
     */
    private suspend fun connectNetworking() {
        runCatching {
            val clientId = "smarthome-guardian-${android.os.Build.ID}"
            mqttManager.connect(BuildConfig.MQTT_BROKER_URL, clientId)
            Timber.d("MQTT connect requested: ${BuildConfig.MQTT_BROKER_URL}")
        }.onFailure { e ->
            Timber.e(e, "MQTT initial connect failed — backoff retry in progress")
        }

        runCatching {
            val accessToken = tokenManager.getAccessToken() ?: return@runCatching
            webSocketManager.connect(BuildConfig.WS_BASE_URL, accessToken)
            Timber.d("WebSocket connect requested: ${BuildConfig.WS_BASE_URL}")
        }.onFailure { e ->
            Timber.e(e, "WebSocket initial connect failed — backoff retry in progress")
        }
    }

    /**
     * 訂閱設備狀態流，將 ONLINE 設備的上線時間回報至 [AlertEngine]。
     *
     * [AlertEngine] 使用此心跳資料追蹤各設備的最後上線時間，
     * 供 [scheduleOfflineCheck] 判斷是否觸發規則 1（設備離線警報）。
     *
     * 使用 [kotlinx.coroutines.flow.collectLatest] 確保設備列表大量更新時，
     * 只處理最新的快照，防止過時的狀態觸發多餘的心跳。
     */
    private suspend fun observeDeviceHeartbeats() {
        deviceRepository.getDevices()
            .catch { e -> Timber.e(e, "Device flow error in SecurityService") }
            .collectLatest { devices ->
                devices.filter { it.status == DeviceStatus.ONLINE }
                    .forEach { alertEngine.onDeviceHeartbeat(it.id) }
            }
    }

    /**
     * 訂閱 [AlertEngine.alertFlow]，將本地規則觸發的警報顯示為系統通知。
     *
     * 每當 AlertEngine 觸發規則（設備離線、重複失敗、非授權時間門鎖、Token 刷新失敗），
     * 此協程收到警報後：
     * 1. 呼叫 [NotificationHelper.showAlertNotification]（CRITICAL 使用全螢幕 Intent）
     * 2. 更新 Foreground Service 通知文字，讓使用者知道有警報發生
     */
    private suspend fun observeAlertEngine() {
        alertEngine.alertFlow
            .catch { e -> Timber.e(e, "AlertEngine flow error in SecurityService") }
            .collect { alert ->
                Timber.i("Alert from engine: ${alert.type} severity=${alert.severity}")
                notificationHelper.showAlertNotification(alert)
                notificationHelper.updateServiceNotification("偵測到 ${alert.severity.displayName} 警報")
            }
    }

    /**
     * 每 [CHECK_INTERVAL_MS] 毫秒執行一次設備離線檢查（AlertEngine 規則 1）。
     *
     * 取得目前所有設備的 ID 列表，傳入 [AlertEngine.checkDeviceOffline]。
     * AlertEngine 會比對每台設備的最後心跳時間，
     * 超過閾值（預設 5 分鐘）的設備觸發 `DEVICE_OFFLINE` 警報。
     *
     * 使用內部短暫的 `delay(200)` + `cancel()` 快照技巧，
     * 在不長期持有 Flow 訂閱的情況下取得設備列表（避免與 [observeDeviceHeartbeats] 的長期訂閱衝突）。
     */
    private suspend fun scheduleOfflineCheck() {
        while (currentCoroutineContext().isActive) {
            delay(CHECK_INTERVAL_MS)
            runCatching {
                val ids = mutableListOf<String>()
                val job = serviceScope.launch {
                    deviceRepository.getDevices()
                        .catch { /* 忽略，下次週期重試 */ }
                        .collect { devices -> ids.addAll(devices.map { it.id }) }
                }
                delay(200)
                job.cancel()
                alertEngine.checkDeviceOffline(ids)
                Timber.d("Offline check: evaluated ${ids.size} device(s)")
            }.onFailure { e ->
                Timber.e(e, "Scheduled offline check failed")
            }
        }
    }

    // ── 靜態輔助 ──────────────────────────────────────────────────────────────

    companion object {
        /**
         * 設備離線定期檢查的時間間隔（毫秒）。
         * 預設 5 分鐘（300,000 ms），平衡電池消耗與離線偵測即時性。
         */
        private const val CHECK_INTERVAL_MS = 5 * 60 * 1_000L

        /**
         * 啟動 SecurityService。
         *
         * 使用 [Context.startForegroundService] 確保 Service 在前景模式啟動，
         * 避免 Android 8+ 背景 Service 限制。
         *
         * 典型呼叫時機：
         * - 使用者認證成功進入 Dashboard 時
         * - APP 從背景恢復至前景時（若 Service 因資源不足被終止）
         *
         * @param context 呼叫端的 [Context]（通常為 `Activity` 或 `Application`）
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, SecurityService::class.java))
            Timber.d("SecurityService start requested")
        }

        /**
         * 停止 SecurityService。
         *
         * 在使用者主動登出時呼叫，停止所有背景監控並清除 Foreground 通知。
         *
         * @param context 呼叫端的 [Context]
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, SecurityService::class.java))
            Timber.d("SecurityService stop requested")
        }
    }
}
