package com.smarthome.guardian.presentation.dashboard

import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.Room
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.SecurityLevel
import com.smarthome.guardian.domain.model.User

/**
 * 儀表板畫面的完整 UI 狀態。
 * 由 [DashboardViewModel] 透過 StateFlow 發布，LoginScreen 依此渲染畫面。
 *
 * @property devices          目前顯示的設備清單（已依房間篩選）
 * @property rooms            房間清單（含「全部」）
 * @property selectedRoom     目前選取的房間
 * @property securityLevel    整體安全等級
 * @property recentAlerts     最新 3 筆警報（底部預覽）
 * @property unreadAlertCount 未確認警報數量（AppBar Badge）
 * @property currentUser      目前登入的使用者資訊
 * @property isLoading        是否正在載入（骨架屏）
 * @property isRefreshing     是否正在下拉重新整理
 * @property error            錯誤訊息（null 表示無錯誤）
 */
data class DashboardUiState(
    val devices: List<Device>       = emptyList(),
    val rooms: List<Room>           = Room.defaults,
    val selectedRoom: Room          = Room.ALL,
    val securityLevel: SecurityLevel = SecurityLevel.SECURE,
    val recentAlerts: List<SecurityAlert> = emptyList(),
    val unreadAlertCount: Int       = 0,
    val currentUser: User?          = null,
    val isLoading: Boolean          = true,
    val isRefreshing: Boolean       = false,
    val error: String?              = null,
)
