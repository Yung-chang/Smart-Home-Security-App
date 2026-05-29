package com.smarthome.guardian.presentation.security

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.SecurityLevel
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.domain.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 安全監控的 ViewModel，同時驅動主監控畫面與警報歷史畫面。
 *
 * 主要職責：
 * 1. 訂閱 [SecurityRepository] 的即時警報 Flow（由 WebSocket 推送，PROMPT 09 接入）
 * 2. 計算安全評分（0–100）
 * 3. 提供警報確認（單筆 / 批次）
 * 4. CRITICAL 警報觸發本地 NotificationManager 彈出
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val deviceRepository: DeviceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── 主監控畫面狀態 ────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    // ── 警報歷史畫面狀態 ──────────────────────────────────────────────────────
    private val _historyState = MutableStateFlow(AlertHistoryUiState())
    val historyState: StateFlow<AlertHistoryUiState> = _historyState.asStateFlow()

    private val _filterState = MutableStateFlow(AlertFilter.NONE)

    init {
        observeSecurityLevel()
        observeLiveAlerts()
        observeDevices()
        observeFilteredHistory()
    }

    // ── 警報確認 ──────────────────────────────────────────────────────────────

    /**
     * 確認（ACK）單筆警報，移除本地通知。
     * @param alertId 目標警報 ID
     */
    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            securityRepository.acknowledgeAlert(alertId)
                .onSuccess {
                    cancelNotification(alertId)
                    Timber.d("Alert acknowledged: $alertId")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    /**
     * 批次確認選取的所有警報。
     * @param alertIds 要確認的警報 ID 集合
     */
    fun bulkAcknowledge(alertIds: List<String>) {
        viewModelScope.launch {
            securityRepository.bulkAcknowledge(alertIds)
                .onSuccess {
                    alertIds.forEach { cancelNotification(it) }
                    _historyState.update { it.copy(selectedIds = emptySet()) }
                    Timber.d("Bulk acknowledged: ${alertIds.size} alerts")
                }
                .onFailure { e ->
                    _historyState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── 批次選取 ──────────────────────────────────────────────────────────────

    fun toggleAlertSelection(alertId: String) {
        _historyState.update { state ->
            val newIds = if (alertId in state.selectedIds)
                state.selectedIds - alertId
            else
                state.selectedIds + alertId
            state.copy(selectedIds = newIds)
        }
    }

    fun clearSelection() = _historyState.update { it.copy(selectedIds = emptySet()) }

    /** 確認目前即時事件列表中所有未確認的警報（一鍵清空）。 */
    fun acknowledgeAllLive() {
        val ids = _uiState.value.liveAlerts
            .filter { !it.isAcknowledged }
            .map { it.id }
        if (ids.isNotEmpty()) bulkAcknowledge(ids)
    }

    // ── 篩選 ──────────────────────────────────────────────────────────────────

    fun updateFilter(filter: AlertFilter) {
        _filterState.value = filter
        _historyState.update { it.copy(filter = filter) }
    }

    fun clearFilter() = updateFilter(AlertFilter.NONE)

    // ── 匯出 ──────────────────────────────────────────────────────────────────

    /**
     * 匯出當前篩選結果為 CSV 或 PDF（完整實作於 PROMPT 08）。
     * @param format "CSV" 或 "PDF"
     */
    fun exportAlerts(format: String) {
        viewModelScope.launch {
            _historyState.update { it.copy(isExporting = true) }
            // TODO PROMPT 08: AuditRepository.exportLogs(filter, format)
            kotlinx.coroutines.delay(1000) // 模擬非同步操作
            _historyState.update { it.copy(isExporting = false) }
            Timber.d("Export requested: format=$format")
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
        _historyState.update { it.copy(error = null) }
    }

    // ── 私有訂閱 ──────────────────────────────────────────────────────────────

    private fun observeSecurityLevel() {
        viewModelScope.launch {
            securityRepository.getSecurityLevel()
                .catch { e -> Timber.e(e, "SecurityLevel flow error") }
                .collect { level ->
                    val score = calculateScore(level)
                    _uiState.update { it.copy(securityLevel = level, securityScore = score) }
                }
        }
    }

    private fun observeLiveAlerts() {
        viewModelScope.launch {
            securityRepository.getAlerts()
                .catch { e -> Timber.e(e, "Alerts flow error") }
                .collect { alerts ->
                    val now = System.currentTimeMillis()
                    val thirtyMin = 30 * 60_000L
                    // 即時事件：未確認（全部保留）+ 30分鐘內已確認（提供操作上下文）
                    val live = alerts
                        .filter { alert ->
                            !alert.isAcknowledged || (now - alert.timestamp < thirtyMin)
                        }
                        .sortedByDescending { it.timestamp }
                        .take(20)
                    _uiState.update { it.copy(liveAlerts = live, isLoading = false) }
                    // CRITICAL 警報立即顯示本地通知
                    live.filter { it.severity == Severity.CRITICAL && !it.isAcknowledged }
                        .forEach { showCriticalNotification(it) }
                }
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.getDevices()
                .catch { e -> Timber.e(e) }
                .collect { devices -> _uiState.update { it.copy(devices = devices) } }
        }
    }

    private fun observeFilteredHistory() {
        viewModelScope.launch {
            combine(
                securityRepository.getAlerts(),
                _filterState,
            ) { alerts, filter -> applyFilter(alerts, filter) }
                .catch { e -> Timber.e(e, "History flow error") }
                .collect { filtered ->
                    val grouped = filtered
                        .sortedByDescending { it.timestamp }
                        .groupBy { formatDateKey(it.timestamp) }
                    _historyState.update { it.copy(groupedAlerts = grouped, isLoading = false) }
                }
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun applyFilter(alerts: List<SecurityAlert>, filter: AlertFilter): List<SecurityAlert> =
        alerts.filter { alert ->
            (filter.startDate == null || alert.timestamp >= filter.startDate) &&
            (filter.endDate == null   || alert.timestamp <= filter.endDate) &&
            (filter.severities.isEmpty() || alert.severity in filter.severities) &&
            (filter.types.isEmpty()      || alert.type in filter.types) &&
            (filter.deviceIds.isEmpty()  || alert.deviceId in filter.deviceIds) &&
            (!filter.onlyUnread          || !alert.isAcknowledged)
        }

    /** 從安全等級推算評分（100 → SECURE，70-99 → WARNING，0-69 → ALERT）。 */
    private fun calculateScore(level: SecurityLevel): Int = when (level) {
        SecurityLevel.SECURE  -> 100
        SecurityLevel.WARNING -> 72
        SecurityLevel.ALERT   -> 30
    }

    private fun formatDateKey(epochMs: Long): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(epochMs))

    private fun showCriticalNotification(alert: SecurityAlert) {
        // 完整實作於 PROMPT 12；此處僅示意
        Timber.w("CRITICAL alert notification: ${alert.message}")
    }

    private fun cancelNotification(alertId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(alertId.hashCode())
    }
}
