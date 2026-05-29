package com.smarthome.guardian.auth

import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.domain.repository.AuthRepository
import com.smarthome.guardian.presentation.auth.AuthErrorCode
import com.smarthome.guardian.presentation.auth.AuthState
import com.smarthome.guardian.presentation.auth.AuthViewModel
import com.smarthome.guardian.presentation.auth.BiometricHelper
import com.smarthome.guardian.security.SecurityCheckResult
import com.smarthome.guardian.security.SecurityChecker
import com.smarthome.guardian.security.SecurityViolation
import com.smarthome.guardian.security.TokenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [AuthViewModel] 單元測試。
 *
 * 以 Mockk 隔離所有外部相依，測試狀態機轉換邏輯。
 * [AuthViewModel.loginWithBiometric] 需要 `FragmentActivity`（Android 執行環境），
 * 其完整路徑在 Instrumented Test 中驗證。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@DisplayName("AuthViewModel")
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository    = mockk<AuthRepository>()
    private val tokenManager      = mockk<TokenManager>(relaxed = true)
    private val biometricHelper   = mockk<BiometricHelper>(relaxed = true)
    private val securityChecker   = mockk<SecurityChecker>()
    private val securePreferences = mockk<SecurePreferences>(relaxed = true)

    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // 預設安全檢查通過（空 violations 清單）
        every { securityChecker.runSecurityChecks() } returns
            SecurityCheckResult(violations = emptyList(), isBypassed = false)
        viewModel = AuthViewModel(authRepository, tokenManager, biometricHelper, securityChecker, securePreferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // ── 初始狀態 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("初始狀態")
    inner class InitialStateTests {

        @Test
        @DisplayName("ViewModel 初始化後狀態為 Idle")
        fun `initial state is Idle`() {
            assertEquals(AuthState.Idle, viewModel.authState.value)
        }

        @Test
        @DisplayName("無 Token 時 checkInitialAuthState → Idle")
        fun `no token stays in Idle`() = runTest {
            every { tokenManager.getAccessToken() }  returns null
            every { tokenManager.getRefreshToken() } returns null

            viewModel.checkInitialAuthState()
            advanceUntilIdle()

            assertEquals(AuthState.Idle, viewModel.authState.value)
        }

        @Test
        @DisplayName("有效 Token + 快取用戶 → Authenticated")
        fun `valid token with cached user results in Authenticated`() = runTest {
            val user = fakeUser()
            every { tokenManager.getAccessToken() }  returns "valid.access.token"
            every { tokenManager.isTokenExpired() }  returns false
            every { authRepository.getCachedUser() } returns user

            viewModel.checkInitialAuthState()
            advanceUntilIdle()

            assertInstanceOf(AuthState.Authenticated::class.java, viewModel.authState.value)
            assertEquals(user, (viewModel.authState.value as AuthState.Authenticated).user)
        }

        @Test
        @DisplayName("Access Token 過期 + 有效 Refresh Token → RequiresBiometric")
        fun `expired access token with valid refresh token requires biometric`() = runTest {
            every { tokenManager.getAccessToken() }        returns "expired.access"
            every { tokenManager.isTokenExpired() }        returns true
            every { tokenManager.getRefreshToken() }       returns "valid.refresh"
            every { tokenManager.isRefreshTokenExpired() } returns false

            viewModel.checkInitialAuthState()
            advanceUntilIdle()

            assertEquals(AuthState.RequiresBiometric, viewModel.authState.value)
        }

        @Test
        @DisplayName("Root 偵測 → Error 狀態（hasHighRiskViolation = true）")
        fun `rooted device transitions to Error`() = runTest {
            every { securityChecker.runSecurityChecks() } returns SecurityCheckResult(
                violations = listOf(SecurityViolation.ROOT_DETECTED),
                isBypassed = false,
            )
            val vm = AuthViewModel(authRepository, tokenManager, biometricHelper, securityChecker, securePreferences)

            vm.checkInitialAuthState()
            advanceUntilIdle()

            assertInstanceOf(AuthState.Error::class.java, vm.authState.value)
        }
    }

    // ── loginWithCredentials ─────────────────────────────────────────────────

    @Nested
    @DisplayName("loginWithCredentials")
    inner class LoginWithCredentialsTests {

        @Test
        @DisplayName("成功登入 → Authenticated（含正確 User）")
        fun `successful login transitions to Authenticated`() = runTest {
            val user = fakeUser()
            coEvery { authRepository.login(any(), any()) } returns Result.success(user)

            viewModel.loginWithCredentials("admin@home.test", "securePass!")
            advanceUntilIdle()

            val state = viewModel.authState.value as? AuthState.Authenticated
            assertEquals(user.id, state?.user?.id)
        }

        @Test
        @DisplayName("帳密錯誤 → Error + INVALID_CREDENTIALS")
        fun `invalid credentials yields Error with INVALID_CREDENTIALS`() = runTest {
            coEvery { authRepository.login(any(), any()) } returns
                Result.failure(IllegalArgumentException("Invalid credentials"))

            viewModel.loginWithCredentials("wrong@test.com", "badPass")
            advanceUntilIdle()

            val state = viewModel.authState.value as? AuthState.Error
            assertEquals(AuthErrorCode.INVALID_CREDENTIALS, state?.code)
        }

        @Test
        @DisplayName("網路超時 → Error + NETWORK_ERROR")
        fun `network timeout yields Error with NETWORK_ERROR`() = runTest {
            coEvery { authRepository.login(any(), any()) } returns
                Result.failure(java.io.IOException("Connection timeout"))

            viewModel.loginWithCredentials("user@test.com", "pass")
            advanceUntilIdle()

            val state = viewModel.authState.value as? AuthState.Error
            assertEquals(AuthErrorCode.NETWORK_ERROR, state?.code)
        }

        @Test
        @DisplayName("Loading 狀態中重複呼叫應被忽略（不重複發 API）")
        fun `duplicate call during Loading is ignored`() = runTest {
            coEvery { authRepository.login(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(500)
                Result.success(fakeUser())
            }

            viewModel.loginWithCredentials("a@b.com", "pass")
            viewModel.loginWithCredentials("a@b.com", "pass") // 應忽略
            advanceUntilIdle()

            coVerify(exactly = 1) { authRepository.login(any(), any()) }
        }
    }

    // ── refreshSession ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshSession")
    inner class RefreshSessionTests {

        @Test
        @DisplayName("Refresh Token 有效 → Authenticated")
        fun `successful refresh yields Authenticated`() = runTest {
            coEvery { authRepository.refreshToken() } returns Result.success(fakeUser())

            viewModel.refreshSession()
            advanceUntilIdle()

            assertInstanceOf(AuthState.Authenticated::class.java, viewModel.authState.value)
        }

        @Test
        @DisplayName("Refresh Token 過期 → 清除 Token + Idle")
        fun `failed refresh clears tokens and returns Idle`() = runTest {
            coEvery { authRepository.refreshToken() } returns Result.failure(Exception("expired"))

            viewModel.refreshSession()
            advanceUntilIdle()

            assertEquals(AuthState.Idle, viewModel.authState.value)
            verify { tokenManager.clearTokens() }
        }
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    inner class LogoutTests {

        @Test
        @DisplayName("登出後回到 Idle")
        fun `logout transitions to Idle`() = runTest {
            coEvery { authRepository.logout() } returns Result.success(Unit)
            coEvery { authRepository.login(any(), any()) } returns Result.success(fakeUser())

            viewModel.loginWithCredentials("a@b.com", "pass")
            advanceUntilIdle()

            viewModel.logout()
            advanceUntilIdle()

            assertEquals(AuthState.Idle, viewModel.authState.value)
        }
    }

    // ── switchToPin ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("switchToPin")
    inner class SwitchToPinTests {

        @Test
        @DisplayName("switchToPin() 立即切換至 RequiresPin")
        fun `switchToPin transitions to RequiresPin immediately`() {
            viewModel.switchToPin()
            assertEquals(AuthState.RequiresPin, viewModel.authState.value)
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun fakeUser() = User(
        id          = "uid-001",
        email       = "admin@home.test",
        name        = "管理員",
        role        = UserRole.ADMIN,
        lastLoginAt = System.currentTimeMillis(),
    )
}
