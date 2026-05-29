package com.smarthome.guardian.presentation.security

import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.SecurityLevel

/**
 * 安全監控主畫面的 UI 狀態。
 *
 * @property securityScore    安全評分 0–100（由 AlertEngine 計算）
 * @property securityLevel    安全等級（SECURE / WARNING / ALERT）
 * @property liveAlerts       即時警報串流（最新在頂部）
 * @property devices          所有設備（供平面圖上色用）
 * @property isLoading        是否載入中
 * @property error            錯誤訊息
 */
data class SecurityUiState(
    val securityScore: Int            = 100,
    val securityLevel: SecurityLevel  = SecurityLevel.SECURE,
    val liveAlerts: List<SecurityAlert> = emptyList(),
    val devices: List<Device>         = emptyList(),
    val isLoading: Boolean            = true,
    val error: String?                = null,
)

/**
 * 警報歷史頁面的 UI 狀態。
 *
 * @property groupedAlerts   按日期分組的警報 Map（日期字串 → 警報列表）
 * @property filter          目前套用的篩選條件
 * @property selectedIds     批次選取的警報 ID 集合
 * @property isExporting     是否正在匯出
 * @property isLoading       是否載入中
 */
data class AlertHistoryUiState(
    val groupedAlerts: Map<String, List<SecurityAlert>> = emptyMap(),
    val filter: AlertFilter         = AlertFilter.NONE,
    val selectedIds: Set<String>    = emptySet(),
    val isExporting: Boolean        = false,
    val isLoading: Boolean          = true,
    val error: String?              = null,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val totalCount: Int get() = groupedAlerts.values.sumOf { it.size }
}
