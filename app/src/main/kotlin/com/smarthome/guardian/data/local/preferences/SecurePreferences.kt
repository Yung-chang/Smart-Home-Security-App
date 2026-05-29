package com.smarthome.guardian.data.local.preferences

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences 的型別安全包裝層。
 *
 * 所有讀寫操作均透過 [SharedPreferences]（實際為 EncryptedSharedPreferences），
 * 確保敏感資料（Token、使用者設定）以 AES-256-SIV（Key）+ AES-256-GCM（Value）加密儲存。
 *
 * 此類別由 [com.smarthome.guardian.di.SecurityModule] 提供，
 * 使用 Hilt 注入時自動取得加密實作。
 *
 * ## 使用限制
 * - 不可在此儲存超過 64KB 的資料（EncryptedSharedPreferences 的限制）
 * - Token 儲存後僅透過此類別存取，不可直接讀取底層 SharedPreferences
 */
@Singleton
class SecurePreferences @Inject constructor(
    private val prefs: SharedPreferences,
) {

    // ── Access Token ──────────────────────────────────────────────────────────

    fun saveAccessToken(token: String) =
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    // ── Refresh Token ─────────────────────────────────────────────────────────

    fun saveRefreshToken(token: String) =
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    // ── 用戶資訊 ──────────────────────────────────────────────────────────────

    fun saveUserId(userId: String) =
        prefs.edit().putString(KEY_USER_ID, userId).apply()

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveUserEmail(email: String) =
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // ── 生物辨識設定 ──────────────────────────────────────────────────────────

    fun setBiometricEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun isBiometricEnabled(): Boolean =
        prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    // ── FCM Token ─────────────────────────────────────────────────────────────

    /** 儲存 FCM 推播 Token（加密保護，避免 Token 洩露被用於偽造推播）。 */
    fun saveFcmToken(token: String) =
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    // ── 清除 ──────────────────────────────────────────────────────────────────

    /** 僅清除 Token（用於 Token 輪換）。 */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    /** 清除所有已儲存的偏好設定（用於登出或帳號移除）。 */
    fun clearAll() = prefs.edit().clear().apply()

    // ── Key 常數 ──────────────────────────────────────────────────────────────

    private companion object {
        const val KEY_ACCESS_TOKEN     = "access_token"
        const val KEY_REFRESH_TOKEN    = "refresh_token"
        const val KEY_USER_ID          = "user_id"
        const val KEY_USER_EMAIL       = "user_email"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_FCM_TOKEN          = "fcm_token"
    }
}
