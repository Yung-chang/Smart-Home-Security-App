package com.smarthome.guardian.security

import java.util.Base64
import com.smarthome.guardian.data.local.preferences.SecurePreferences

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 JWT Access Token 與 Refresh Token 的儲存、讀取及過期判斷。
 *
 * 所有 Token 透過 [SecurePreferences]（EncryptedSharedPreferences）儲存，
 * 不會出現在普通的 SharedPreferences 或記憶體快取中。
 *
 * ## Token 生命週期
 * - Access Token：15 分鐘有效（由伺服器 `exp` claim 決定）
 * - Refresh Token：7 天有效，儲存於 EncryptedSharedPreferences
 *
 * ## 安全注意事項
 * - 不可在日誌中輸出完整 Token 字串
 * - [clearTokens] 在登出時必須立即呼叫
 */
@Singleton
class TokenManager @Inject constructor(
    private val securePreferences: SecurePreferences,
) {

    // ── 儲存 ──────────────────────────────────────────────────────────────────

    /**
     * 儲存 JWT access token 及 refresh token。
     * 覆寫舊有 Token，呼叫前不需先清除。
     *
     * @param access  新的 JWT Access Token（Bearer）
     * @param refresh 新的 Refresh Token
     */
    fun saveTokens(access: String, refresh: String) {
        securePreferences.saveAccessToken(access)
        securePreferences.saveRefreshToken(refresh)
        Timber.d("Tokens saved (access exp: ${runCatching { getExpiryEpoch(access) }.getOrNull()})")
    }

    // ── 讀取 ──────────────────────────────────────────────────────────────────

    /**
     * 回傳目前儲存的 Access Token，若尚未登入則回傳 `null`。
     */
    fun getAccessToken(): String? = securePreferences.getAccessToken()

    /**
     * 回傳目前儲存的 Refresh Token，若尚未登入則回傳 `null`。
     */
    fun getRefreshToken(): String? = securePreferences.getRefreshToken()

    // ── 過期檢查 ──────────────────────────────────────────────────────────────

    /**
     * 判斷目前的 Access Token 是否已過期或即將過期。
     *
     * 採用提前 60 秒失效策略（clock skew buffer），避免邊界情況下
     * Token 在伺服器端被視為過期但 APP 尚未更新。
     *
     * @return `true` 表示 Token 不存在、格式錯誤或已（即將）過期
     */
    fun isTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return runCatching {
            val expEpochSec = getExpiryEpoch(token) ?: return true
            val nowSec      = System.currentTimeMillis() / 1000L
            nowSec >= (expEpochSec - EXPIRY_BUFFER_SECONDS)
        }.getOrElse {
            Timber.w(it, "Failed to parse token expiry — treating as expired")
            true
        }
    }

    /**
     * 判斷目前的 Refresh Token 是否已過期。
     * Refresh Token 過期時需要重新完整登入（生物辨識 + PIN）。
     */
    fun isRefreshTokenExpired(): Boolean {
        val token = getRefreshToken() ?: return true
        return runCatching {
            val expEpochSec = getExpiryEpoch(token) ?: return true
            System.currentTimeMillis() / 1000L >= expEpochSec
        }.getOrElse { true }
    }

    // ── 清除 ──────────────────────────────────────────────────────────────────

    /**
     * 清除所有已儲存的 Token（登出時呼叫）。
     * 清除後 [getAccessToken] 與 [getRefreshToken] 均回傳 `null`。
     */
    fun clearTokens() {
        securePreferences.clearTokens()
        Timber.d("All tokens cleared")
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /**
     * 解析 JWT Payload 中的 `exp` claim（Unix epoch，單位：秒）。
     *
     * JWT 結構：`header.payload.signature`（均為 Base64URL 編碼）
     *
     * @param jwt JWT 字串
     * @return `exp` 欄位的 epoch 秒數，解析失敗時回傳 `null`
     * @throws TokenParseException JWT 格式完全無效時
     */
    private fun getExpiryEpoch(jwt: String): Long? {
        val parts = jwt.split(".")
        if (parts.size != 3) throw TokenParseException("Invalid JWT format: expected 3 parts")

        // Base64URL → Base64 Standard（補 padding）
        val payloadBase64 = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { s ->
                val pad = (4 - s.length % 4) % 4
                s + "=".repeat(pad)
            }

        return runCatching {
            val payloadJson = String(Base64.getDecoder().decode(payloadBase64), Charsets.UTF_8)
            Regex(""""exp"\s*:\s*(\d+)""").find(payloadJson)?.groupValues?.get(1)?.toLongOrNull()
        }.getOrNull()
    }

    companion object {
        /** 提前 60 秒視為過期，避免 clock skew 造成邊界問題。 */
        private const val EXPIRY_BUFFER_SECONDS = 60L
    }
}
