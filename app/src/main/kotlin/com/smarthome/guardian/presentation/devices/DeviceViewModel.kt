package com.smarthome.guardian.presentation.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.domain.model.CommandType
import com.smarthome.guardian.domain.model.DeviceCommand
import com.smarthome.guardian.domain.model.DeviceOperation
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
                }
        }
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

    // ── 私有：實際發送 ────────────────────────────────────────────────────────

    private fun sendCommandInternal(command: DeviceCommand) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingCommand = true) }
            // TODO PROMPT 07: checkPermission(command)
            // TODO PROMPT 08: auditLogger.log(DEVICE_CONTROL, deviceId, command)
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
