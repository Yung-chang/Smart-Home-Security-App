package com.smarthome.guardian.security

import com.smarthome.guardian.data.local.preferences.SecurePreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [TokenManager] 的單元測試。
 * 使用 Mockk 模擬 [SecurePreferences]，隔離 EncryptedSharedPreferences 依賴。
 */
@DisplayName("TokenManager")
class TokenManagerTest {

    private lateinit var securePreferences: SecurePreferences
    private lateinit var tokenManager: TokenManager

    // 有效 JWT（exp = 9999999999，約 2286 年，永不過期）
    // Header: {"alg":"HS256","typ":"JWT"}
    // Payload: {"sub":"user123","exp":9999999999}
    private val validNonExpiredToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJzdWIiOiJ1c2VyMTIzIiwiZXhwIjo5OTk5OTk5OTk5fQ" +
        ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    // 已過期的 JWT（exp = 1000000000，約 2001 年）
    // Payload: {"sub":"user123","exp":1000000000}
    private val expiredToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJzdWIiOiJ1c2VyMTIzIiwiZXhwIjoxMDAwMDAwMDAwfQ" +
        ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    // 無 exp claim 的 JWT
    // Payload: {"sub":"user123"}
    private val noExpToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJzdWIiOiJ1c2VyMTIzIn0" +
        ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @BeforeEach
    fun setUp() {
        securePreferences = mockk(relaxed = true)
        tokenManager = TokenManager(securePreferences)
    }

    // ── saveTokens ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveTokens")
    inner class SaveTokensTest {

        @Test
        @DisplayName("應呼叫 SecurePreferences 儲存 access 與 refresh token")
        fun `should save both tokens`() {
            tokenManager.saveTokens("access_jwt", "refresh_jwt")

            verify { securePreferences.saveAccessToken("access_jwt") }
            verify { securePreferences.saveRefreshToken("refresh_jwt") }
        }
    }

    // ── getAccessToken ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccessToken")
    inner class GetAccessTokenTest {

        @Test
        @DisplayName("未登入時應回傳 null")
        fun `returns null when not logged in`() {
            every { securePreferences.getAccessToken() } returns null
            assertNull(tokenManager.getAccessToken())
        }

        @Test
        @DisplayName("已登入時應回傳 token")
        fun `returns token when logged in`() {
            every { securePreferences.getAccessToken() } returns validNonExpiredToken
            assertEquals(validNonExpiredToken, tokenManager.getAccessToken())
        }
    }

    // ── isTokenExpired ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isTokenExpired")
    inner class IsTokenExpiredTest {

        @Test
        @DisplayName("未儲存 token 時應視為過期")
        fun `null token is expired`() {
            every { securePreferences.getAccessToken() } returns null
            assertTrue(tokenManager.isTokenExpired())
        }

        @Test
        @DisplayName("有效（未來過期）token 應視為未過期")
        fun `valid non-expired token is not expired`() {
            every { securePreferences.getAccessToken() } returns validNonExpiredToken
            assertFalse(tokenManager.isTokenExpired())
        }

        @Test
        @DisplayName("已過期 token 應視為過期")
        fun `expired token is expired`() {
            every { securePreferences.getAccessToken() } returns expiredToken
            assertTrue(tokenManager.isTokenExpired())
        }

        @Test
        @DisplayName("無 exp claim 的 token 應視為過期")
        fun `token without exp claim is expired`() {
            every { securePreferences.getAccessToken() } returns noExpToken
            assertTrue(tokenManager.isTokenExpired())
        }

        @Test
        @DisplayName("格式完全錯誤的 token 應視為過期（不拋出例外）")
        fun `malformed token is treated as expired`() {
            every { securePreferences.getAccessToken() } returns "not.a.jwt.at.all"
            assertTrue(tokenManager.isTokenExpired())
        }

        @Test
        @DisplayName("空字串 token 應視為過期")
        fun `empty token is expired`() {
            every { securePreferences.getAccessToken() } returns ""
            assertTrue(tokenManager.isTokenExpired())
        }
    }

    // ── clearTokens ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearTokens")
    inner class ClearTokensTest {

        @Test
        @DisplayName("應呼叫 SecurePreferences.clearTokens")
        fun `should delegate to SecurePreferences`() {
            tokenManager.clearTokens()
            verify { securePreferences.clearTokens() }
        }
    }
}
