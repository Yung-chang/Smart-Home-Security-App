package com.smarthome.guardian.data.remote.websocket

import com.google.gson.annotations.SerializedName

/**
 * WebSocket 原始訊息包裝（伺服器端 JSON 格式）。
 *
 * ## 訊息格式（JSON）
 * ```json
 * { "type": "DEVICE_STATUS",   "deviceId": "...", "status": "ONLINE", "isOn": true, ... }
 * { "type": "SECURITY_ALERT",  "alertType": "INTRUSION", "severity": "CRITICAL", ... }
 * { "type": "SYSTEM_EVENT",    "eventType": "GATEWAY_CONNECTED", "message": "..." }
 * { "type": "PONG",            "echoTimestamp": 1700000000000 }
 * ```
 *
 * 由 [WebSocketManager] 解析後分發至對應的 [kotlinx.coroutines.flow.SharedFlow]。
 */
data class WsRawMessage(
    @SerializedName("type")         val type: String        = "",

    // DEVICE_STATUS
    @SerializedName("deviceId")     val deviceId: String?   = null,
    @SerializedName("status")       val status: String?     = null,
    @SerializedName("isOn")         val isOn: Boolean?      = null,
    @SerializedName("batteryLevel") val batteryLevel: Int?  = null,
    @SerializedName("signalStrength")val signalStrength: Int?= null,

    // SECURITY_ALERT
    @SerializedName("alertId")      val alertId: String?    = null,
    @SerializedName("alertType")    val alertType: String?  = null,
    @SerializedName("severity")     val severity: String?   = null,
    @SerializedName("message")      val message: String?    = null,

    // SYSTEM_EVENT
    @SerializedName("eventType")    val eventType: String?  = null,

    // PONG
    @SerializedName("echoTimestamp")val echoTimestamp: Long?= null,

    // 共用
    @SerializedName("timestamp")    val timestamp: Long     = System.currentTimeMillis(),
    @SerializedName("data")         val data: Map<String, String> = emptyMap(),
)

/** WebSocket 訊息類型常數（對應 [WsRawMessage.type]）。 */
object WsMessageType {
    const val DEVICE_STATUS  = "DEVICE_STATUS"
    const val SECURITY_ALERT = "SECURITY_ALERT"
    const val SYSTEM_EVENT   = "SYSTEM_EVENT"
    const val PING           = "PING"
    const val PONG           = "PONG"
    const val ERROR          = "ERROR"
}

/** WebSocket 連線狀態。 */
enum class WebSocketState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
}
