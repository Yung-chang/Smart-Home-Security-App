package com.smarthome.guardian.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.DeviceType
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity

// ── 設備 DTO ──────────────────────────────────────────────────────────────────

data class DeviceResponse(
    @SerializedName("id")             val id: String,
    @SerializedName("name")           val name: String,
    @SerializedName("type")           val type: String,
    @SerializedName("room_id")        val roomId: String,
    @SerializedName("status")         val status: String,
    @SerializedName("is_locked")      val isLocked: Boolean = false,
    @SerializedName("last_seen")      val lastSeen: Long = 0L,
    @SerializedName("firmware")       val firmware: String = "1.0.0",
    @SerializedName("mac_address")    val macAddress: String = "",
    @SerializedName("is_on")          val isOn: Boolean = false,
    @SerializedName("battery_level")  val batteryLevel: Int? = null,
    @SerializedName("signal_strength")val signalStrength: Int? = null,
) {
    fun toDomain(): Device = Device(
        id            = id,
        name          = name,
        type          = runCatching { DeviceType.valueOf(type.uppercase()) }.getOrDefault(DeviceType.SENSOR_MOTION),
        roomId        = roomId,
        status        = runCatching { DeviceStatus.valueOf(status.uppercase()) }.getOrDefault(DeviceStatus.OFFLINE),
        isLocked      = isLocked,
        lastSeen      = lastSeen,
        firmware      = firmware,
        macAddress    = macAddress,
        isOn          = isOn,
        batteryLevel  = batteryLevel,
        signalStrength= signalStrength,
        accessRules   = emptyList(), // 由 AccessRule 端點獨立載入
    )
}

data class DeviceListResponse(
    @SerializedName("devices") val devices: List<DeviceResponse> = emptyList(),
    @SerializedName("total")   val total: Int = 0,
)

// ── 設備指令 DTO ──────────────────────────────────────────────────────────────

data class DeviceCommandRequest(
    @SerializedName("command_id")  val commandId: String,
    @SerializedName("type")        val type: String,
    @SerializedName("parameters")  val parameters: Map<String, String>,
    @SerializedName("timestamp")   val timestamp: Long,
    @SerializedName("signature")   val signature: String,
)

// ── 安全警報 DTO ──────────────────────────────────────────────────────────────

data class SecurityAlertResponse(
    @SerializedName("id")              val id: String,
    @SerializedName("type")            val type: String,
    @SerializedName("severity")        val severity: String,
    @SerializedName("device_id")       val deviceId: String?,
    @SerializedName("message")         val message: String,
    @SerializedName("timestamp")       val timestamp: Long,
    @SerializedName("is_acknowledged") val isAcknowledged: Boolean = false,
    @SerializedName("action_taken")    val actionTaken: String?,
) {
    fun toDomain(): SecurityAlert = SecurityAlert(
        id             = id,
        type           = runCatching { AlertType.valueOf(type.uppercase()) }.getOrDefault(AlertType.SYSTEM),
        severity       = runCatching { Severity.valueOf(severity.uppercase()) }.getOrDefault(Severity.MEDIUM),
        deviceId       = deviceId,
        message        = message,
        timestamp      = timestamp,
        isAcknowledged = isAcknowledged,
        actionTaken    = actionTaken,
    )
}

data class AlertListResponse(
    @SerializedName("alerts") val alerts: List<SecurityAlertResponse> = emptyList(),
    @SerializedName("total")  val total: Int = 0,
)

data class AcknowledgeRequest(
    @SerializedName("alert_ids") val alertIds: List<String>,
)

// ── 設備設定 DTO ──────────────────────────────────────────────────────────────

data class DeviceSettingsRequest(
    @SerializedName("name")          val name: String? = null,
    @SerializedName("room_id")       val roomId: String? = null,
    @SerializedName("is_locked")     val isLocked: Boolean? = null,
    @SerializedName("settings")      val settings: Map<String, String> = emptyMap(),
)
