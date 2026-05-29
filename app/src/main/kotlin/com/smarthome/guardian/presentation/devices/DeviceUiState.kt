package com.smarthome.guardian.presentation.devices

import com.smarthome.guardian.domain.model.AccessLog
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceCommand
import com.smarthome.guardian.domain.model.SensorReading
import com.smarthome.guardian.domain.model.SensorSnapshot

/**
 * 設備詳情頁的完整 UI 狀態。
 *
 * @property device           目前設備資料
 * @property isLoading        是否載入中
 * @property error            錯誤訊息
 * @property isSendingCommand 指令發送中（防止重複點擊）
 * @property commandSuccess   最後一次指令成功的描述（用於顯示 Snackbar）
 * @property showConfirmDialog 是否顯示確認對話框
 * @property pendingCommand   待確認的指令（確認後執行）
 * @property accessLogs       門鎖進出記錄
 * @property tempCode         生成的臨時密碼（顯示後應清除）
 * @property sensorSnapshots  感應器當前值快照
 * @property sensorHistory    感應器歷史讀取值（圖表用）
 * @property brightness       燈光亮度 0–100
 * @property colorTemp        色溫 2700–6500K
 * @property lightColor       燈光顏色（ARGB Int）
 */
data class DeviceUiState(
    val device: Device? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSendingCommand: Boolean = false,
    val commandSuccess: String? = null,
    // 確認對話框
    val showConfirmDialog: Boolean = false,
    val pendingCommand: DeviceCommand? = null,
    val confirmTitle: String = "",
    val confirmMessage: String = "",
    // 門鎖
    val accessLogs: List<AccessLog> = emptyList(),
    val tempCode: String? = null,
    // 感應器
    val sensorSnapshots: List<SensorSnapshot> = emptyList(),
    val sensorHistory: List<SensorReading> = emptyList(),
    val threshold: Float = 0f,
    // 燈光
    val brightness: Float = 100f,
    val colorTemp: Float = 4000f,
    val lightColor: Int = 0xFFFFFFFF.toInt(),
    // 攝影機
    val sensitivity: Float = 50f,
    val recordings: List<RecordingEntry> = emptyList(),
    // 溫控器
    val targetTemp: Float = 24f,
    val acMode: String = "冷氣",
)

/** 錄影記錄項目。 */
data class RecordingEntry(
    val id: String,
    val startTime: Long,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
)
