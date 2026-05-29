package com.smarthome.guardian.presentation.auth

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.domain.repository.AuthRepository
import com.smarthome.guardian.security.SecurityChecker
import com.smarthome.guardian.security.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 驗證流程的 ViewModel。
 *
 * 以 [AuthState] StateFlow 驅動 UI，所有狀態轉換均在此集中管理。
 *
 * ## 登入流程
 * 1. 首次登入：Email + 密碼 → [loginWithCredentials]
 * 2. 後續開啟 APP：先嘗試生物辨識 → [loginWithBiometric]
 * 3. 生物辨識失敗備援：PIN → [verifyPin]
 * 4. Access Token 過期時：靜默更新 → [refreshSession]
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val biometricHelper: BiometricHelper,
    private val securityChecker: SecurityChecker,
    private val securePreferences: SecurePreferences,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // StateFlow 讓 Compose 能訂閱變化，解決 getter 只在首次組合時讀值的問題
    private val _rememberedEmail    = MutableStateFlow(securePreferences.getRememberedEmail() ?: "")
    private val _rememberMeEnabled  = MutableStateFlow(securePreferences.isRememberMeEnabled())
    val rememberedEmail:   StateFlow<String>  = _rememberedEmail.asStateFlow()
    val rememberMeEnabled: StateFlow<Boolean> = _rememberMeEnabled.asStateFlow()

    /** 更新記住我設定（由 UI 層勾選框呼叫）。 */
    fun setRememberMe(enabled: Boolean, email: String = "") {
        securePreferences.setRememberMe(enabled)
        _rememberMeEnabled.value = enabled
        if (enabled && email.isNotBlank()) {
            securePreferences.saveRememberedEmail(email)
            _rememberedEmail.value = email
        } else if (!enabled) {
            securePreferences.clearRememberedEmail()
            _rememberedEmail.value = ""
        }
    }

    // ── 初始化檢查 ─────────��──────────────────────────────────────────────────

    /**
     * APP 啟動時呼叫：判斷應顯示登入畫面、生物辨識畫面或直接進入主畫面。
     */
    fun checkInitialAuthState() {
        viewModelScope.launch {
            val securityResult = securityChecker.runSecurityChecks()
            if (securityResult.hasHighRiskViolation) {
                _authState.value = AuthState.Error(
                    message = "裝置安全性不符合要求，無法啟動應用程式",
                    code = AuthErrorCode.UNKNOWN,
                )
                return@launch
            }

            when {
                tokenManager.getAccessToken() != null && !tokenManager.isTokenExpired() -> {
                    // Token 仍有效，嘗試靜默恢復 Session
                    authRepository.getCachedUser()?.let { user ->
                        _authState.value = AuthState.Authenticated(user)
                        return@launch
                    }
                    refreshSession()
                }
                tokenManager.getRefreshToken() != null && !tokenManager.isRefreshTokenExpired() -> {
                    // Access Token 過期但 Refresh Token 有效 → 要求生物辨識確認身份
                    _authState.value = AuthState.RequiresBiometric
                }
                else -> {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    // ── Email + 密碼登入 ────��─────────────────────────────────────────────────

    /**
     * 使用 Email 與密碼進行完整登入。
     * 登入成功後 Token 自動儲存，後續可使用生物辨識解鎖。
     *
     * @param email    使用者 Email
     * @param password 密碼（不得記錄至日誌）
     */
    fun loginWithCredentials(email: String, password: String) {
        if (_authState.value is AuthState.Loading) return
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            authRepository.login(email, password)
                .onSuccess { user ->
                    Timber.d("Credentials login success: ${user.id}")
                    if (securePreferences.isRememberMeEnabled()) {
                        securePreferences.saveRememberedEmail(email)
                    }
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(
                        message = e.message ?: "登入失敗，請稍後再試",
                        code = mapExceptionToErrorCode(e),
                    )
                }
        }
    }

    // ── 生物辨識登入 ──────────────────────────────────────────────────────────

    /**
     * 使用生物辨識（指紋/臉部）解鎖已有的 Session。
     *
     * 生物辨識成功後呼叫 [refreshSession] 取得新的 Access Token；
     * 失敗時轉換至 [AuthState.RequiresPin] 提供 PIN 備援。
     *
     * @param activity 宿主 Activity（BiometricPrompt 所需）
     */
    fun loginWithBiometric(activity: FragmentActivity) {
        if (_authState.value is AuthState.Loading) return

        _authState.value = AuthState.Loading

        biometricHelper.authenticate(
            activity  = activity,
            title     = "SmartHome Guardian",
            subtitle  = "請使用生物辨識登入",
            onSuccess = {
                if (BuildConfig.DEBUG) {
                    // Debug：生物辨識成功直接以測試帳號登入（無後端）
                    _authState.value = AuthState.Authenticated(
                        User(id = "bio-user-001", email = "test@smarthome.local",
                             name = "測試管理員", role = UserRole.ADMIN)
                    )
                } else {
                    viewModelScope.launch { refreshSession() }
                }
            },
            onFailure = {
                // 單次識別失敗，保持 Loading 讓用戶重試
            },
            onError   = { errorCode, message ->
                _authState.value = when (errorCode) {
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthState.Error(
                        message = "生物辨識已鎖定，請使用 PIN 碼",
                        code    = AuthErrorCode.BIOMETRIC_LOCKOUT,
                    )
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED     -> AuthState.RequiresPin
                    else                                    -> AuthState.Error(
                        message = message,
                        code    = AuthErrorCode.BIOMETRIC_FAILED,
                    )
                }
            },
        )
    }

    // ── PIN 驗證 ───────────────────��────────────────────────────────���─────────

    /**
     * 使用 PIN 碼進行備援驗證。
     * PIN 格式驗證（長度、數字）在 UI 層完成，此處直接送至伺服器驗證。
     *
     * @param pin 用戶輸入的 PIN 碼
     */
    fun verifyPin(pin: String) {
        if (_authState.value is AuthState.Loading) return

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // 優先從快取用戶或已儲存的 Email 取得，不依賴可能已過期的 Access Token
            // （PIN 備援的使用場景正是 Access Token 過期時，直接讀 Token 永遠會失敗）
            val email = authRepository.getCachedUser()?.email
                ?: securePreferences.getUserEmail()
                ?: tokenManager.getAccessToken()?.let { extractEmailFromToken(it) }
                ?: run {
                    _authState.value = AuthState.Error("請重新登入", AuthErrorCode.TOKEN_EXPIRED)
                    return@launch
                }

            authRepository.verifyPin(email, pin)
                .onSuccess { user -> _authState.value = AuthState.Authenticated(user) }
                .onFailure { e ->
                    _authState.value = AuthState.Error(
                        message = e.message ?: "PIN 碼錯誤",
                        code    = mapExceptionToErrorCode(e),
                    )
                }
        }
    }

    // ── 登出 ──────────────────────────────────────────────────────────────────

    /** 登出：撤銷 Token 並重置至 [AuthState.Idle]。 */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.Idle
        }
    }

    // ── Session 更新 ────────────────────────��─────────────────────────────────

    /**
     * 使用 Refresh Token 靜默更新 Access Token。
     * Refresh Token 無效時切換至 [AuthState.Idle] 要求重新登入。
     */
    fun refreshSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.refreshToken()
                .onSuccess { user -> _authState.value = AuthState.Authenticated(user) }
                .onFailure {
                    tokenManager.clearTokens()
                    _authState.value = AuthState.Idle
                }
        }
    }

    // ── 切換至 PIN ────────────────────────────────────────────────────────────

    /** 用戶主動選擇 PIN 備援時呼叫。 */
    fun switchToPin() {
        _authState.value = AuthState.RequiresPin
    }

    /** PIN 對話框取消時重置至 Idle。 */
    fun resetToIdle() {
        _authState.value = AuthState.Idle
    }

    /** Debug 用：直接以測試帳號通過 PIN 驗證。 */
    fun mockPinSuccess() {
        _authState.value = AuthState.Authenticated(
            User(id = "pin-user-001", email = "test@smarthome.local",
                 name = "測試管理員", role = UserRole.ADMIN)
        )
    }

    /** 是否已設定本地 PIN 碼。 */
    fun hasLocalPin(): Boolean = authRepository.hasLocalPin()

    /**
     * 設定本地 PIN 碼並在 Debug 模式下直接登入。
     * Release 模式：只儲存 PIN，使用者之後可以用 PIN 登入。
     */
    fun setupPin(pin: String) {
        viewModelScope.launch {
            authRepository.setupLocalPin(pin)
                .onSuccess {
                    // 設定完成後立即以相同 PIN 驗證，直接進入主畫面（Release + Debug 均適用）
                    if (BuildConfig.DEBUG) {
                        _authState.value = AuthState.Authenticated(
                            User(id = "pin-user-001", email = "test@smarthome.local",
                                 name = "測試管理員", role = UserRole.ADMIN)
                        )
                    } else {
                        val email = authRepository.getCachedUser()?.email
                            ?: securePreferences.getUserEmail()
                            ?: run {
                                _authState.value = AuthState.Error(
                                    "請先以帳號密碼登入後再設定 PIN 碼", AuthErrorCode.TOKEN_EXPIRED)
                                return@onSuccess
                            }
                        authRepository.verifyPin(email, pin)
                            .onSuccess { user -> _authState.value = AuthState.Authenticated(user) }
                            .onFailure { e ->
                                _authState.value = AuthState.Error(
                                    message = e.message ?: "設定 PIN 碼失敗",
                                    code    = AuthErrorCode.UNKNOWN,
                                )
                            }
                    }
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(
                        message = e.message ?: "設定 PIN 碼失敗",
                        code    = AuthErrorCode.UNKNOWN,
                    )
                }
        }
    }

    // ── 私有工具 ────────────────────────────��─────────────────────────────────

    private fun mapExceptionToErrorCode(e: Throwable): AuthErrorCode = when (e) {
        is IllegalArgumentException -> AuthErrorCode.INVALID_CREDENTIALS
        is IllegalStateException    -> AuthErrorCode.PIN_LOCKED
        is java.io.IOException      -> AuthErrorCode.NETWORK_ERROR
        else                        -> AuthErrorCode.UNKNOWN
    }

    /** 從 JWT Payload 提取 email claim（用於 PIN 驗證）。 */
    private fun extractEmailFromToken(jwt: String): String? = runCatching {
        val payload = jwt.split(".").getOrNull(1) ?: return@runCatching null
        val padded  = payload.replace('-', '+').replace('_', '/').let { s ->
            s + "=".repeat((4 - s.length % 4) % 4)
        }
        val json = org.json.JSONObject(String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT)))
        if (json.has("email")) json.getString("email") else null
    }.getOrNull()
}
