package com.smarthome.guardian.presentation.navigation

/** 所有 Navigation Compose 路由的集中定義。 */
object AppRoutes {
    const val LOGIN      = "login"
    const val BIOMETRIC  = "biometric"
    const val PIN        = "pin"
    const val DASHBOARD  = "dashboard"
    const val DEVICE_DETAIL = "device/{deviceId}"
    const val SECURITY   = "security"
    const val SETTINGS   = "settings"
    const val ADD_DEVICE = "add_device"

    const val ALERT_HISTORY   = "alert_history"
    const val ACCESS_CONTROL  = "access_control"
    const val AUDIT_LOGS      = "audit_logs"

    fun deviceDetail(deviceId: String) = "device/$deviceId"
}
