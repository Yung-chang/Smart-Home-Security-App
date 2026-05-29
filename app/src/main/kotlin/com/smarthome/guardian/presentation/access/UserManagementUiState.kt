package com.smarthome.guardian.presentation.access

import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.QrCodeData
import com.smarthome.guardian.domain.model.User

/**
 * 存取控制主畫面的 UI 狀態（涵蓋三個 Tab）。
 *
 * @property users           所有使用者清單
 * @property rules           所有存取規則
 * @property conflictPairs   衝突規則對（顯示紅色警告）
 * @property qrCodes         已生成的 QR Code 清單
 * @property selectedUser    目前選取的使用者（BottomSheet 顯示用）
 * @property selectedTab     目前選取的 Tab（0=用戶 / 1=規則 / 2=臨時存取）
 * @property showInviteDialog 是否顯示邀請對話框
 * @property showRuleWizard  是否顯示規則新增精靈
 * @property isLoading       是否載入中
 * @property error           錯誤訊息
 * @property successMessage  成功訊息（用於 Snackbar）
 */
data class UserManagementUiState(
    val users: List<User>                           = emptyList(),
    val devices: List<Device>                       = emptyList(),
    val rules: List<AccessRule>                     = emptyList(),
    val conflictPairs: List<Pair<AccessRule, AccessRule>> = emptyList(),
    val qrCodes: List<QrCodeData>                   = emptyList(),
    val selectedUser: User?                         = null,
    val selectedTab: Int                            = 0,
    val showInviteDialog: Boolean                   = false,
    val showRuleWizard: Boolean                     = false,
    val isLoading: Boolean                          = true,
    val error: String?                              = null,
    val successMessage: String?                     = null,
)
