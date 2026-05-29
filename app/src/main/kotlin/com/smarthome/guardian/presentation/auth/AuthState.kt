package com.smarthome.guardian.presentation.auth

import com.smarthome.guardian.domain.model.User

/**
 * 驗證流程的 UI 狀態機。
 * 由 [AuthViewModel.authState]（StateFlow）驅動，LoginScreen 依此渲染畫面。
 */
sealed class AuthState {

    /** 初始狀態，尚未發起任何驗證操作。 */
    data object Idle : AuthState()

    /** 正在進行網路請求或生物辨識等非同步操作。 */
    data object Loading : AuthState()

    /** 需要生物辨識（已有 Refresh Token，但 Access Token 過期）。 */
    data object RequiresBiometric : AuthState()

    /** 需要 PIN 碼（生物辨識失敗或用戶選擇 PIN 備援）。 */
    data object RequiresPin : AuthState()

    /** 驗證成功，[user] 為目前登入的使用者。 */
    data class Authenticated(val user: User) : AuthState()

    /**
     * 驗證失敗。
     *
     * @property message 向用戶顯示的錯誤描述（不得含敏感資訊）
     * @property code    機器可讀的錯誤代碼，用於決定 UI 行為
     */
    data class Error(
        val message: String,
        val code: AuthErrorCode,
    ) : AuthState()
}

/**
 * 驗證錯誤代碼。
 * UI 層根據代碼決定是否顯示重試按鈕、跳至 PIN 備援等行為。
 */
enum class AuthErrorCode {
    /** 帳號或密碼錯誤。 */
    INVALID_CREDENTIALS,

    /** 裝置不支援生物辨識感應器。 */
    BIOMETRIC_NOT_AVAILABLE,

    /** 生物辨識辨識失敗（指紋/臉部不符）。 */
    BIOMETRIC_FAILED,

    /** 生物辨識嘗試次數過多，已暫時鎖定。 */
    BIOMETRIC_LOCKOUT,

    /** PIN 碼錯誤。 */
    PIN_INCORRECT,

    /** PIN 碼嘗試次數過多，帳號已鎖定。 */
    PIN_LOCKED,

    /** 網路連線失敗或伺服器無回應。 */
    NETWORK_ERROR,

    /** Token 過期且無法自動更新（需重新完整登入）。 */
    TOKEN_EXPIRED,

    /** 其他未預期錯誤。 */
    UNKNOWN,
}
