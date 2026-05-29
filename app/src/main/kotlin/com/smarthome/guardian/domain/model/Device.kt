package com.smarthome.guardian.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 家庭智慧設備的領域模型。
 *
 * @property id            UUID
 * @property name          顯示名稱
 * @property type          設備類型（決定圖示與控制面板）
 * @property roomId        所在房間 ID
 * @property status        連線狀態
 * @property isLocked      是否被管理員鎖定（鎖定後一般用戶無法操作）
 * @property lastSeen      最後上線 Unix epoch 毫秒
 * @property firmware      韌體版本字串
 * @property macAddress    MAC 位址（用於識別，不用於傳輸）
 * @property encryptionKey 設備通訊密鑰（已加密儲存，不可直接讀取明文）
 * @property accessRules   此設備的存取規則清單
 */
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val roomId: String,
    val status: DeviceStatus,
    val isLocked: Boolean = false,
    val lastSeen: Long = 0L,
    val firmware: String = "1.0.0",
    val macAddress: String = "",
    val encryptionKey: String = "",
    val accessRules: List<AccessRule> = emptyList(),
    // 可操控設備的當前狀態值（燈光開關、門鎖狀態等）
    val isOn: Boolean = false,
    val batteryLevel: Int? = null,       // 0-100，null 表示不適用
    val signalStrength: Int? = null,     // 0-100，null 表示不適用
)

/** 設備類型枚舉，也承載 UI 顯示所需的圖示與中文名稱。 */
enum class DeviceType(val displayName: String) {
    LIGHT("智慧燈光"),
    DOOR_LOCK("智慧門鎖"),
    CAMERA("監控攝影機"),
    SENSOR_MOTION("移動感應器"),
    SENSOR_DOOR("門窗感應器"),
    OUTLET("智慧插座"),
    THERMOSTAT("溫控器"),
    ALARM("警報器");

    val icon: ImageVector get() = when (this) {
        LIGHT         -> Icons.Filled.Lightbulb
        DOOR_LOCK     -> Icons.Filled.Lock
        CAMERA        -> Icons.Filled.Videocam
        SENSOR_MOTION -> Icons.Filled.Sensors
        SENSOR_DOOR   -> Icons.Filled.SensorDoor
        OUTLET        -> Icons.Filled.Power
        THERMOSTAT    -> Icons.Filled.Thermostat
        ALARM         -> Icons.Filled.NotificationImportant
    }

    /** 是否支援開/關快速切換（感應器類不支援）。 */
    val isToggleable: Boolean get() = this in setOf(LIGHT, DOOR_LOCK, OUTLET, ALARM)
}

/** 設備連線狀態。 */
enum class DeviceStatus {
    ONLINE,
    OFFLINE,
    ERROR;

    val isOperational: Boolean get() = this == ONLINE
}
