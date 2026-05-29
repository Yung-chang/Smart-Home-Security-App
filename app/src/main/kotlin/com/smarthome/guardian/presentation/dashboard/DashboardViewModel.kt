package com.smarthome.guardian.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.DeviceType
import com.smarthome.guardian.domain.model.Room
import java.util.UUID
import com.smarthome.guardian.domain.repository.AuthRepository
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.domain.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 儀表板的 ViewModel。
 *
 * 合併以下三個資料來源，組成單一 [DashboardUiState] StateFlow：
 * - [DeviceRepository]：設備清單（WebSocket 即時更新）
 * - [SecurityRepository]：安全等級 + 警報
 * - [AuthRepository]：目前登入用戶
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val securityRepository: SecurityRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _selectedRoom = MutableStateFlow(Room.ALL)

    init {
        observeDevices()
        observeSecurityLevel()
        observeAlerts()
        loadCurrentUser()
    }

    // ── 房間篩選 ──────────────────────────────────────────────────────────────

    /** 切換房間篩選器。 */
    fun selectRoom(room: Room) {
        _selectedRoom.value = room
        _uiState.update { it.copy(selectedRoom = room) }
    }

    // ── 設備操作 ──────────────────────────────────────────────────────────────

    /**
     * 快速切換設備開/關狀態（Toggle）。
     * 指令發送前會更新本地 UI 狀態（Optimistic Update），
     * 失敗時回復原狀態並顯示錯誤訊息。
     *
     * @param deviceId 目標設備 ID
     * @param newState true = 開啟，false = 關閉
     */
    fun toggleDevice(deviceId: String, newState: Boolean) {
        // Optimistic update
        _uiState.update { state ->
            state.copy(
                devices = state.devices.map { device ->
                    if (device.id == deviceId) device.copy(isOn = newState) else device
                }
            )
        }

        viewModelScope.launch {
            val operation = if (newState) DeviceOperation.TOGGLE_ON else DeviceOperation.TOGGLE_OFF
            deviceRepository.sendCommand(deviceId, operation)
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle device $deviceId")
                    // 回復 Optimistic update
                    _uiState.update { state ->
                        state.copy(
                            devices = state.devices.map { device ->
                                if (device.id == deviceId) device.copy(isOn = !newState) else device
                            },
                            error = "設備操作失敗：${e.message}",
                        )
                    }
                }
        }
    }

    /** 清除錯誤訊息 Snackbar。 */
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** 新增設備至本地資料庫並立即顯示在清單。 */
    fun addDevice(name: String, type: DeviceType, roomId: String) {
        viewModelScope.launch {
            val device = Device(
                id     = UUID.randomUUID().toString(),
                name   = name.trim(),
                type   = type,
                roomId = roomId,
                status = DeviceStatus.OFFLINE,
            )
            deviceRepository.addDevice(device)
                .onSuccess { Timber.d("Device added: ${device.name}") }
                .onFailure { e -> _uiState.update { it.copy(error = "新增設備失敗：${e.message}") } }
        }
    }

    // ── 下拉重新整理 ──────────────────────────────────────────────────────────

    /** 強制從伺服器重新拉取所有設備資料。 */
    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            deviceRepository.refresh()
                .onFailure { e ->
                    _uiState.update { it.copy(error = "重新整理失敗：${e.message}") }
                }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── 私有訂閱 ──────────────────────────────────────────────────────────────

    private fun observeDevices() {
        viewModelScope.launch {
            _selectedRoom
                .flatMapLatest { room ->
                    if (room.id == Room.ALL.id) deviceRepository.getDevices()
                    else deviceRepository.getDevicesByRoom(room.id)
                }
                .catch { e -> Timber.e(e, "Device flow error") }
                .collect { devices ->
                    _uiState.update { it.copy(devices = devices, isLoading = false) }
                }
        }
    }

    private fun observeSecurityLevel() {
        viewModelScope.launch {
            securityRepository.getSecurityLevel()
                .catch { e -> Timber.e(e, "Security level flow error") }
                .collect { level ->
                    _uiState.update { it.copy(securityLevel = level) }
                }
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            combine(
                securityRepository.getRecentAlerts(limit = 3),
                securityRepository.getUnreadAlertCount(),
            ) { recent, count -> Pair(recent, count) }
                .catch { e -> Timber.e(e, "Alerts flow error") }
                .collect { (recent, count) ->
                    _uiState.update { it.copy(recentAlerts = recent, unreadAlertCount = count) }
                }
        }
    }

    private fun loadCurrentUser() {
        _uiState.update { it.copy(currentUser = authRepository.getCachedUser()) }
    }
}
