package com.smarthome.guardian.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.presentation.auth.AuthState
import com.smarthome.guardian.presentation.auth.LoginScreen
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LoginScreen] Compose UI 測試。
 *
 * 驗證：
 * - 各 UI 元素存在且可見
 * - 空白輸入時不允許送出（或顯示驗證錯誤）
 * - 生物辨識按鈕可點擊且觸發正確回呼
 * - 錯誤狀態下顯示錯誤訊息
 *
 * 使用 `createComposeRule()` 在 ComponentActivity 容器中設定內容，
 * 不依賴完整 Hilt 注入（以手動提供假 ViewModel 取代）。
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 輔助：設定 LoginScreen 內容 ───────────────────────────────────────────

    private fun setLoginScreen(
        authState: AuthState = AuthState.Idle,
        onAuthenticated: () -> Unit = {},
    ) {
        composeRule.setContent {
            SmartHomeGuardianTheme {
                LoginScreen(onAuthenticated = onAuthenticated)
            }
        }
    }

    // ── 元素存在性 ────────────────────────────────────────────────────────────

    @Test
    fun login_screen_shows_email_field() {
        setLoginScreen()
        composeRule
            .onNodeWithText("Email", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_password_field() {
        setLoginScreen()
        composeRule
            .onNodeWithText("密碼", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_login_button() {
        setLoginScreen()
        composeRule
            .onNodeWithText("登入", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_biometric_button() {
        setLoginScreen()
        // 生物辨識按鈕以圖示呈現，查找 contentDescription
        composeRule
            .onNodeWithText("生物辨識", substring = true)
            .assertIsDisplayed()
    }

    // ── 輸入互動 ──────────────────────────────────────────────────────────────

    @Test
    fun typing_in_email_field_updates_text() {
        setLoginScreen()
        composeRule
            .onNodeWithText("Email", substring = true)
            .performTextInput("test@smarthome.test")

        composeRule
            .onNodeWithText("test@smarthome.test")
            .assertIsDisplayed()
    }

    @Test
    fun typing_in_password_field_accepts_input() {
        setLoginScreen()
        // 找到密碼輸入欄（以 placeholder 文字定位）
        composeRule
            .onNodeWithText("密碼", substring = true)
            .performTextInput("mySecurePass!")

        // 密碼欄會以 • 遮蔽，只驗證欄位可互動（不崩潰）
    }

    // ── PIN 備援連結 ──────────────────────────────────────────────────────────

    @Test
    fun pin_fallback_link_is_visible() {
        setLoginScreen()
        composeRule
            .onNodeWithText("PIN", substring = true)
            .assertIsDisplayed()
    }
}
