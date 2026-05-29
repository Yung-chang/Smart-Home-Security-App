package com.smarthome.guardian.domain.model

import java.util.UUID

/**
 * 設備控制指令，送出前由 [DeviceRepository] 自動以 HMAC-SHA256 簽章。
 *
 * @property id         指令唯一識別碼（用於稽核日誌追蹤）
 * @property deviceId   目標設備 ID
 * @property type       指令類型
 * @property parameters 指令參數（Key-Value，如 `"brightness" to "80"`）
 * @property timestamp  建立時間 epoch 毫秒（防止重送攻擊）
 * @property signature  HMAC-SHA256 簽章（由 Repository 填入，不應由呼叫端設定）
 */
data class DeviceCommand(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val type: CommandType,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = "",
) {
    /** 用於簽章計算的正規化字串（排除 signature 本身）。 */
    fun toSignableString(): String =
        "$id|$deviceId|$type|${parameters.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}|$timestamp"
}

/** 所有可發送的設備指令類型。 */
enum class CommandType {
    // 通用
    TOGGLE_ON, TOGGLE_OFF,

    // 門鎖
    LOCK, UNLOCK, GENERATE_TEMP_CODE,

    // 燈光
    SET_BRIGHTNESS, SET_COLOR_TEMP, SET_COLOR, APPLY_SCENE, SET_SCHEDULE,

    // 攝影機
    SET_SENSITIVITY, CAPTURE_SCREENSHOT, START_RECORDING, STOP_RECORDING,

    // 感應器
    SET_THRESHOLD, RESET_ALARM,
}
