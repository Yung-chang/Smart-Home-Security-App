package com.smarthome.guardian.presentation.access

import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.QrCodeData
import com.smarthome.guardian.domain.model.User

/**
 * 存取控制主畫面的整合狀態。
 * 同時為三個 Tab 提供資料。
 */
data class AccessControlUiState(
    // ── 用戶 Tab ──────────────────────────────────────────────────────────────
    val users: List<User>                  = emptyList(),
    val isLoadingUsers: Boolean            = true,

    // ── 存取規則 Tab ──────────────────────────────────────────────────────────
    val rules: List<AccessRule>            = emptyList(),
    val conflictPairs: List<Pair<AccessRule, AccessRule>> = emptyList(),
    val isLoadingRules: Boolean            = true,

    // ── 臨時存取 Tab ──────────────────────────────────────────────────────────
    val qrCodes: List<QrCodeData>          = emptyList(),
    val isLoadingQr: Boolean               = false,
    val newlyGeneratedQr: QrCodeData?      = null,

    // ── 裝置清單（新增規則精靈用）────────────────────────────────────────────
    val devices: List<Device>              = emptyList(),

    // ── 通用 ──────────────────────────────────────────────────────────────────
    val error: String?                     = null,
    val successMessage: String?            = null,
)

/** 新增規則精靈的暫存狀態（4 個步驟）。 */
data class AddRuleWizardState(
    val step: Int = 1,
    val selectedDeviceId: String = "",
    val selectedUserId: String   = "",
    val useTimeWindow: Boolean   = false,
    val selectedDays: Set<java.time.DayOfWeek> = emptySet(),
    val startHour: Int   = 8,
    val startMinute: Int = 0,
    val endHour: Int     = 22,
    val endMinute: Int   = 0,
    val selectedOperations: Set<com.smarthome.guardian.domain.model.DeviceOperation> = emptySet(),
) {
    val isComplete: Boolean
        get() = selectedDeviceId.isNotBlank() &&
                selectedUserId.isNotBlank() &&
                selectedOperations.isNotEmpty()
}
