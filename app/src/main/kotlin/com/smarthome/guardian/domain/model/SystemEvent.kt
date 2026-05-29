package com.smarthome.guardian.domain.model

/**
 * 系統級事件，由 WebSocket 或 AlertEngine 發出。
 *
 * 與 [SecurityAlert] 的差異：SystemEvent 為資訊性通知（不需 ACK），
 * 而 SecurityAlert 需要操作員確認。
 *
 * @property type      事件類型
 * @property message   人類可讀描述
 * @property timestamp 發生時間 epoch 毫秒
 * @property data      附加鍵值資料（依事件類型而異）
 */
data class SystemEvent(
    val type: SystemEventType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, String> = emptyMap(),
)

enum class SystemEventType {
    GATEWAY_CONNECTED,
    GATEWAY_DISCONNECTED,
    FIRMWARE_UPDATE_AVAILABLE,
    CONFIG_CHANGED,
    SECURITY_MODE_CHANGED,
    NETWORK_CHANGED,
    TOKEN_REFRESHED,
    SESSION_EXPIRED,
}
