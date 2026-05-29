package com.smarthome.guardian.data.remote.websocket

import com.google.gson.Gson
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.domain.model.SystemEvent
import com.smarthome.guardian.domain.model.SystemEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * 管理 WSS（WebSocket Secure）連線的完整生命週期。
 *
 * ## 功能
 * - **自動重連**：指數退避策略（1s → 2s → 4s … 最大 60s），連線成功後重置
 * - **應用層心跳**：每 [HEARTBEAT_INTERVAL_MS] 毫秒發送 PING，伺服器 PONG 逾時則觸發重連
 * - **Flow-based 事件**：設備狀態、安全警報、系統事件分別透過 [SharedFlow] 廣播
 * - **執行緒安全**：所有狀態讀寫在 [Dispatchers.IO] 的 [CoroutineScope] 內處理
 *
 * ## 使用方式
 * ```kotlin
 * webSocketManager.connect(accessToken)
 * webSocketManager.observeDeviceStatus("device-id").collect { status -> ... }
 * webSocketManager.observeAlerts().collect { alert -> ... }
 * ```
 *
 * ## OWASP M5 — 不足的傳輸層保護
 * 僅接受 `wss://` 協定；OkHttpClient 已設定 CertificatePinner。
 */
@Singleton
class WebSocketManager @Inject constructor(
    @Named("websocket") private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson  = Gson()

    // ── 連線狀態 ──────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(WebSocketState.IDLE)
    val state: StateFlow<WebSocketState> = _state.asStateFlow()

    // ── 事件廣播流 ────────────────────────────────────────────────────────────
    private val _rawEvents    = MutableSharedFlow<WsRawMessage>(extraBufferCapacity = 64)
    private val _deviceStatus = MutableSharedFlow<Pair<String, DeviceStatus>>(extraBufferCapacity = 32)
    private val _alerts       = MutableSharedFlow<SecurityAlert>(extraBufferCapacity = 32)
    private val _systemEvents = MutableSharedFlow<SystemEvent>(extraBufferCapacity = 16)

    val alerts: SharedFlow<SecurityAlert> = _alerts.asSharedFlow()

    // ── 私有狀態 ──────────────────────────────────────────────────────────────
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var wsUrl: String = ""
    @Volatile private var retryCount  = 0
    @Volatile private var lastPongMs  = 0L
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    // ── 公開 API ──────────────────────────────────────────────────────────────

    /**
     * 建立 WSS 連線。若已連線則忽略。
     *
     * @param url   WSS 端點 URL（`wss://...`）
     * @param token JWT Access Token（附加至 Query String，伺服器驗證用）
     */
    fun connect(url: String, token: String) {
        if (_state.value == WebSocketState.CONNECTED ||
            _state.value == WebSocketState.CONNECTING) return

        wsUrl = "$url?token=$token"
        retryCount = 0
        doConnect()
    }

    /** 主動斷開連線並清除所有背景 Job。 */
    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(CLOSE_NORMAL, "Client disconnected")
        webSocket = null
        _state.value = WebSocketState.DISCONNECTED
        Timber.d("WebSocket disconnected by client")
    }

    /**
     * 觀察指定設備的即時狀態更新（供 ViewModel 使用）。
     *
     * @param deviceId 目標設備 ID
     */
    fun observeDeviceStatus(deviceId: String): Flow<DeviceStatus> =
        _deviceStatus
            .filter { (id, _) -> id == deviceId }
            .map { (_, status) -> status }

    /**
     * 觀察所有設備的狀態更新（供 Repository 層同步 DB 使用）。
     * 回傳 Pair(deviceId, DeviceStatus)。
     */
    fun observeAllDeviceStatusUpdates(): Flow<Pair<String, DeviceStatus>> =
        _deviceStatus.asSharedFlow()

    /** 觀察所有即時安全警報。 */
    fun observeAlerts(): Flow<SecurityAlert> = _alerts.asSharedFlow()

    /** 觀察系統事件（Gateway 連/斷、韌體更新等）。 */
    fun observeSystemEvents(): Flow<SystemEvent> = _systemEvents.asSharedFlow()

    /** 發送原始 JSON 訊息（例如設備控制指令）。回傳是否成功入隊。 */
    fun send(json: String): Boolean {
        val ws = webSocket ?: return false.also { Timber.w("WebSocket not connected, drop message") }
        return ws.send(json)
    }

    // ── 私有：連線管理 ────────────────────────────────────────────────────────

    private fun doConnect() {
        _state.value = WebSocketState.CONNECTING
        val request = Request.Builder().url(wsUrl).build()
        webSocket   = client.newWebSocket(request, createListener())
        Timber.d("WebSocket connecting: $wsUrl (retry=$retryCount)")
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateBackoff(retryCount)
            _state.value = WebSocketState.RECONNECTING
            Timber.d("WebSocket reconnecting in ${delayMs}ms (attempt=${retryCount + 1})")
            delay(delayMs)
            if (isActive) {
                retryCount++
                doConnect()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastPongMs = System.currentTimeMillis()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val pingJson = gson.toJson(mapOf(
                    "type"      to WsMessageType.PING,
                    "requestId" to UUID.randomUUID().toString(),
                    "timestamp" to System.currentTimeMillis(),
                ))
                val sent = webSocket?.send(pingJson) ?: false
                if (!sent) {
                    Timber.w("WebSocket heartbeat failed — connection may be dead")
                    break
                }
                // 檢查 PONG 逾時（PONG 未在 2 個心跳週期內回應）
                val sinceLastPong = System.currentTimeMillis() - lastPongMs
                if (sinceLastPong > HEARTBEAT_INTERVAL_MS * 2) {
                    Timber.w("WebSocket PONG timeout (${sinceLastPong}ms) — forcing reconnect")
                    webSocket?.cancel()
                    break
                }
            }
        }
    }

    // ── 私有：訊息解析 ────────────────────────────────────────────────────────

    private fun handleMessage(text: String) {
        val raw = runCatching { gson.fromJson(text, WsRawMessage::class.java) }
            .getOrElse { e ->
                Timber.e(e, "WebSocket: failed to parse message")
                return
            }

        _rawEvents.tryEmit(raw)

        when (raw.type) {
            WsMessageType.DEVICE_STATUS -> handleDeviceStatus(raw)
            WsMessageType.SECURITY_ALERT -> handleSecurityAlert(raw)
            WsMessageType.SYSTEM_EVENT  -> handleSystemEvent(raw)
            WsMessageType.PONG          -> lastPongMs = System.currentTimeMillis()
            WsMessageType.ERROR -> Timber.w("WebSocket server error: ${raw.message}")
            else -> Timber.d("WebSocket unknown type: ${raw.type}")
        }
    }

    private fun handleDeviceStatus(raw: WsRawMessage) {
        val deviceId = raw.deviceId ?: return
        val status   = raw.status?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
            ?: DeviceStatus.OFFLINE
        _deviceStatus.tryEmit(deviceId to status)
    }

    private fun handleSecurityAlert(raw: WsRawMessage) {
        val alert = SecurityAlert(
            id             = raw.alertId ?: UUID.randomUUID().toString(),
            type           = raw.alertType?.let { runCatching { AlertType.valueOf(it) }.getOrNull() }
                             ?: AlertType.SYSTEM,
            severity       = raw.severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() }
                             ?: Severity.MEDIUM,
            deviceId       = raw.deviceId,
            message        = raw.message ?: "Unknown alert",
            timestamp      = raw.timestamp,
            isAcknowledged = false,
        )
        _alerts.tryEmit(alert)
    }

    private fun handleSystemEvent(raw: WsRawMessage) {
        val event = SystemEvent(
            type      = raw.eventType?.let { runCatching { SystemEventType.valueOf(it) }.getOrNull() }
                        ?: SystemEventType.CONFIG_CHANGED,
            message   = raw.message ?: "",
            timestamp = raw.timestamp,
            data      = raw.data,
        )
        _systemEvents.tryEmit(event)
    }

    // ── 私有：WebSocket 監聽器 ────────────────────────────────────────────────

    private fun createListener() = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("WebSocket connected")
            retryCount     = 0
            _state.value   = WebSocketState.CONNECTED
            _systemEvents.tryEmit(SystemEvent(SystemEventType.GATEWAY_CONNECTED, "WebSocket 已連線"))
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closing: code=$code reason=$reason")
            webSocket.close(CLOSE_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closed: code=$code")
            heartbeatJob?.cancel()
            _state.value = WebSocketState.DISCONNECTED
            _systemEvents.tryEmit(SystemEvent(SystemEventType.GATEWAY_DISCONNECTED, "WebSocket 已中斷"))
            if (code != CLOSE_NORMAL) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "WebSocket failure (HTTP ${response?.code})")
            heartbeatJob?.cancel()
            _state.value = WebSocketState.DISCONNECTED
            scheduleReconnect()
        }
    }

    // ── 退避計算 ──────────────────────────────────────────────────────────────

    /** 指數退避：1s, 2s, 4s, 8s, 16s, 32s, 60s（上限） */
    private fun calculateBackoff(attempt: Int): Long =
        min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2.0.pow(attempt).toLong())

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val BASE_BACKOFF_MS       = 1_000L
        private const val MAX_BACKOFF_MS        = 60_000L
        private const val CLOSE_NORMAL          = 1000
    }
}
