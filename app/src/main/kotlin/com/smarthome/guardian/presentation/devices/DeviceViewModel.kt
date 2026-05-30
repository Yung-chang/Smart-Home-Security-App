package com.smarthome.guardian.presentation.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.domain.model.AccessAction
import com.smarthome.guardian.domain.model.AccessLog
import com.smarthome.guardian.domain.model.CommandType
import com.smarthome.guardian.domain.model.DeviceCommand
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.model.DeviceType
import com.smarthome.guardian.domain.model.SensorReading
import com.smarthome.guardian.domain.model.SensorSnapshot
import com.smarthome.guardian.domain.model.UnlockMethod
import com.smarthome.guardian.data.logger.AuditLogger
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 設備詳情頁的 ViewModel。
 *
 * 核心流程：
 * 1. 由 [SavedStateHandle] 取得 `deviceId` 導航參數
 * 2. 訂閱 [DeviceRepository.getDevice] 取得即時設備狀態
 * 3. 每個控制操作先驗證權限（Access Control，完整實作於 PROMPT 07）
 * 4. 重要操作先顯示確認對話框，確認後才呼叫 Repository
 * 5. 所有操作寫入稽核日誌（完整實作於 PROMPT 08）
 */
@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val auditLogger: AuditLogger,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        observeDevice()
    }

    // ── 設備狀態訂閱 ──────────────────────────────────────────────────────────

    private fun observeDevice() {
        viewModelScope.launch {
            deviceRepository.getDevice(deviceId)
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { device ->
                    _uiState.update { it.copy(device = device, isLoading = false) }
                    // 感應器：注入示範歷史資料讓圖表可以顯示
                    if (device != null &&
                        (device.type == DeviceType.SENSOR_MOTION || device.type == DeviceType.SENSOR_DOOR) &&
                        _uiState.value.sensorHistory.isEmpty()) {
                        injectDemoSensorData(device.type)
                    }
                    // 門鎖：注入示範進出記錄
                    if (device != null && device.type == DeviceType.DOOR_LOCK &&
                        _uiState.value.accessLogs.isEmpty()) {
                        injectDemoAccessLogs(device.id)
                    }
                }
        }
    }

    private fun injectDemoAccessLogs(deviceId: String) {
        val now = System.currentTimeMillis()
        val logs = listOf(
            AccessLog(id = "$deviceId-log-1", deviceId = deviceId,
                userId = "test-user-001", userName = "測試管理員",
                action = AccessAction.UNLOCK, method = UnlockMethod.APP,
                timestamp = now - 25_000L),
            AccessLog(id = "$deviceId-log-2", deviceId = deviceId,
                userId = null, userName = "訪客 A",
                action = AccessAction.TEMP_CODE_USED, method = UnlockMethod.TEMP_CODE,
                timestamp = now - 3_600_000L),
            AccessLog(id = "$deviceId-log-3", deviceId = deviceId,
                userId = "test-user-001", userName = "測試管理員",
                action = AccessAction.LOCK, method = UnlockMethod.AUTO_LOCK,
                timestamp = now - 7_200_000L),
            AccessLog(id = "$deviceId-log-4", deviceId = deviceId,
                userId = null, userName = "未知",
                action = AccessAction.FAILED_ATTEMPT, method = UnlockMethod.PIN,
                timestamp = now - 14_400_000L, isSuccessful = false),
            AccessLog(id = "$deviceId-log-5", deviceId = deviceId,
                userId = "family-001", userName = "家庭成員",
                action = AccessAction.UNLOCK, method = UnlockMethod.FINGERPRINT,
                timestamp = now - 86_400_000L),
            AccessLog(id = "$deviceId-log-6", deviceId = deviceId,
                userId = "family-001", userName = "家庭成員",
                action = AccessAction.LOCK, method = UnlockMethod.PHYSICAL_KEY,
                timestamp = now - 90_000_000L),
        )
        _uiState.update { it.copy(accessLogs = logs) }
    }

    private fun injectDemoSensorData(type: DeviceType) {
        val now   = System.currentTimeMillis()
        val unit  = if (type == DeviceType.SENSOR_MOTION) "次" else "次"
        val base  = if (type == DeviceType.SENSOR_MOTION) 3f else 1f
        val history = (23 downTo 0).map { hoursAgo ->
            SensorReading(
                timestamp = now - hoursAgo * 3_600_000L,
                value     = (base + Math.random().toFloat() * 5f).coerceAtLeast(0f),
                unit      = unit,
            )
        }
        val snapshots = listOf(
            SensorSnapshot(label = if (type == DeviceType.SENSOR_MOTION) "今日觸發次數" else "開關次數",
                value = history.takeLast(24).sumOf { it.value.toDouble() }.toFloat(), unit = unit),
            SensorSnapshot(label = "最近 1 小時", value = history.last().value, unit = unit),
        )
        _uiState.update { it.copy(sensorHistory = history, sensorSnapshots = snapshots) }
    }

    // ── 指令發送（需確認）────────────────────────────────────────────────────

    /**
     * 發送需要使用者確認的重要指令（如門鎖上鎖/解鎖）。
     * 呼叫後顯示確認對話框，確認後呼叫 [executeConfirmedCommand]。
     */
    fun requestCommand(
        command: DeviceCommand,
        confirmTitle: String,
        confirmMessage: String,
    ) {
        _uiState.update {
            it.copy(
                showConfirmDialog = true,
                pendingCommand    = command,
                confirmTitle      = confirmTitle,
                confirmMessage    = confirmMessage,
            )
        }
    }

    /** 使用者點擊「確認」後執行 [pendingCommand]。 */
    fun executeConfirmedCommand() {
        val command = _uiState.value.pendingCommand ?: return
        _uiState.update { it.copy(showConfirmDialog = false, pendingCommand = null) }
        sendCommandInternal(command)
    }

    /** 取消確認對話框。 */
    fun cancelCommand() {
        _uiState.update { it.copy(showConfirmDialog = false, pendingCommand = null) }
    }

    // ── 直接指令（不需確認）──────────────────────────────────────────────────

    /**
     * 發送不需確認的指令（如亮度調整、靈敏度調整等連續操作）。
     * 同時以 Optimistic Update 更新 UI 狀態。
     */
    fun sendCommand(command: DeviceCommand) = sendCommandInternal(command)

    // ── 開關（通用）──────────────────────────────────────────────────────────

    fun toggleDevice(newState: Boolean) {
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = if (newState) CommandType.TOGGLE_ON else CommandType.TOGGLE_OFF,
        ))
    }

    // ── 燈光控制 ──────────────────────────────────────────────────────────────

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_BRIGHTNESS,
            parameters = mapOf("value" to brightness.toInt().toString()),
        ))
    }

    fun setColorTemp(kelvin: Float) {
        _uiState.update { it.copy(colorTemp = kelvin) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_COLOR_TEMP,
            parameters = mapOf("kelvin" to kelvin.toInt().toString()),
        ))
    }

    fun setColor(colorArgb: Int) {
        _uiState.update { it.copy(lightColor = colorArgb) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_COLOR,
            parameters = mapOf("argb" to colorArgb.toString()),
        ))
    }

    fun applyScene(sceneName: String) {
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.APPLY_SCENE,
            parameters = mapOf("scene" to sceneName),
        ))
    }

    // ── 攝影機控制 ────────────────────────────────────────────────────────────

    fun setSensitivity(value: Float) {
        _uiState.update { it.copy(sensitivity = value) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_SENSITIVITY,
            parameters = mapOf("value" to value.toInt().toString()),
        ))
    }

    fun captureScreenshot() {
        sendCommand(DeviceCommand(deviceId = deviceId, type = CommandType.CAPTURE_SCREENSHOT))
    }

    // ── 感應器控制 ────────────────────────────────────────────────────────────

    fun setThreshold(value: Float) {
        _uiState.update { it.copy(threshold = value) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_THRESHOLD,
            parameters = mapOf("value" to value.toString()),
        ))
    }

    // ── 門鎖：臨時密碼 ────────────────────────────────────────────────────────

    fun generateTempCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingCommand = true) }
            deviceRepository.sendCommand(
                deviceId  = deviceId,
                operation = DeviceOperation.UNLOCK,
                payload   = """{"action":"generate_temp_code"}""",
            ).onSuccess {
                // 實際由後端回傳，這裡模擬一個 6 位數字
                val code = (100000..999999).random().toString()
                _uiState.update { it.copy(tempCode = code, isSendingCommand = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isSendingCommand = false) }
            }
        }
    }

    fun clearTempCode() = _uiState.update { it.copy(tempCode = null) }
    fun clearError()    = _uiState.update { it.copy(error = null) }
    fun clearSuccess()  = _uiState.update { it.copy(commandSuccess = null) }

    // ── 溫控器控制 ────────────────────────────────────────────────────────────

    fun setTargetTemp(temp: Float) {
        _uiState.update { it.copy(targetTemp = temp.coerceIn(16f, 30f)) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.SET_THRESHOLD,
            parameters = mapOf("targetTemp" to temp.toInt().toString()),
        ))
    }

    fun setAcMode(mode: String) {
        _uiState.update { it.copy(acMode = mode) }
        sendCommand(DeviceCommand(
            deviceId   = deviceId,
            type       = CommandType.APPLY_SCENE,
            parameters = mapOf("acMode" to mode),
        ))
    }

    // ── 警報器控制 ────────────────────────────────────────────────────────────

    fun setAlarmVolume(volume: Float) {
        _uiState.update { it.copy(alarmVolume = volume) }
        sendCommand(DeviceCommand(deviceId = deviceId, type = CommandType.SET_THRESHOLD,
            parameters = mapOf("volume" to volume.toInt().toString())))
    }

    fun setAlarmDelay(delay: String) = _uiState.update { it.copy(alarmDelay = delay) }

    fun silenceAlarm() {
        _uiState.update { it.copy(alarmTriggered = false) }
        sendCommand(DeviceCommand(deviceId = deviceId, type = CommandType.RESET_ALARM))
    }

    fun testAlarm() = _uiState.update { it.copy(alarmTriggered = true) }

    // ── 定時開關 ──────────────────────────────────────────────────────────────

    fun setSchedule(enabled: Boolean, onTime: String, offTime: String) {
        _uiState.update { it.copy(
            scheduleEnabled = enabled,
            scheduleOnTime  = onTime,
            scheduleOffTime = offTime,
        )}
        sendCommand(DeviceCommand(deviceId = deviceId, type = CommandType.SET_SCHEDULE,
            parameters = mapOf("enabled" to enabled.toString(),
                               "on" to onTime, "off" to offTime)))
    }

    // ── 私有：實際發送 ────────────────────────────────────────────────────────

    private fun sendCommandInternal(command: DeviceCommand) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingCommand = true) }
            auditLogger.log(
                action   = AuditAction.DEVICE_CONTROL,
                targetId = command.deviceId,
                before   = mapOf("type" to command.type.name),
                after    = command.parameters,
            )
            deviceRepository.sendCommand(
                deviceId  = command.deviceId,
                operation = DeviceOperation.TOGGLE, // repository maps CommandType → Operation
                payload   = command.toSignableString(),
            ).onSuccess {
                Timber.d("Command sent: ${command.type}")
                _uiState.update { it.copy(isSendingCommand = false, commandSuccess = command.type.name) }
            }.onFailure { e ->
                Timber.e(e, "Command failed: ${command.type}")
                _uiState.update { it.copy(isSendingCommand = false, error = "指令失敗：${e.message}") }
            }
        }
    }
}
