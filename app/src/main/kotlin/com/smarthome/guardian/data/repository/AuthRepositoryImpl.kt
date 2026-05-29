package com.smarthome.guardian.data.repository

import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.data.remote.api.ApiService
import com.smarthome.guardian.data.remote.dto.LoginRequest
import com.smarthome.guardian.data.remote.dto.RefreshTokenRequest
import com.smarthome.guardian.data.remote.dto.VerifyPinRequest
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.domain.repository.AuthRepository
import com.smarthome.guardian.security.TokenManager
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] 的具體實作，透過 Retrofit [ApiService] 呼叫後端。
 *
 * Token 管理完全委託給 [TokenManager]，此類別不直接操作 SharedPreferences。
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) : AuthRepository {

    // 記憶體快取，避免每次都重新解析 Token
    private var cachedUser: User? = null

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        // ── Debug 測試帳號（僅 Debug build 有效，Release 完全移除）───────────
        if (BuildConfig.DEBUG && email == "test@smarthome.local" && password == "Test1234!") {
            val enc     = java.util.Base64.getUrlEncoder().withoutPadding()
            val header  = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
            val payload = enc.encodeToString("""{"sub":"test-001","exp":9999999999}""".toByteArray())
            val mockJwt = "$header.$payload.mock-sig"
            tokenManager.saveTokens(mockJwt, mockJwt)
            return@runCatching User(
                id    = "test-user-001",
                email = email,
                name  = "測試管理員",
                role  = UserRole.ADMIN,
            ).also { cachedUser = it }
        }

        // 裝置指紋：實際實作應整合 SecurityModule 的裝置識別碼
        val fingerprint = android.os.Build.FINGERPRINT.take(64)

        val response = apiService.login(LoginRequest(email, password, fingerprint))

        if (!response.isSuccessful) {
            val code = response.code()
            throw when (code) {
                401  -> IllegalArgumentException("帳號或密碼錯誤")
                423  -> IllegalStateException("帳號已被鎖定，請聯絡管理員")
                else -> IOException("伺服器錯誤（HTTP $code）")
            }
        }

        val body = response.body() ?: throw IOException("伺服器回傳空回應")
        tokenManager.saveTokens(body.accessToken, body.refreshToken)
        val user = body.user.toDomain()
        cachedUser = user
        Timber.d("Login success: userId=${user.id}, role=${user.role}")
        user
    }

    override suspend fun verifyPin(email: String, pin: String): Result<User> = runCatching {
        val response = apiService.verifyPin(VerifyPinRequest(pin, email))

        if (!response.isSuccessful) {
            throw when (response.code()) {
                401  -> IllegalArgumentException("PIN 碼錯誤")
                423  -> IllegalStateException("PIN 嘗試次數過多，帳號已鎖定")
                else -> IOException("伺服器錯誤（HTTP ${response.code()}）")
            }
        }

        val body = response.body() ?: throw IOException("伺服器回傳空回應")
        tokenManager.saveTokens(body.accessToken, body.refreshToken)
        val user = body.user.toDomain()
        cachedUser = user
        user
    }

    override suspend fun refreshToken(): Result<User> = runCatching {
        val refreshToken = tokenManager.getRefreshToken()
            ?: throw IllegalStateException("No refresh token available")

        val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))

        if (!response.isSuccessful) {
            tokenManager.clearTokens()
            throw IllegalStateException("Refresh token expired or revoked (HTTP ${response.code()})")
        }

        val body = response.body() ?: throw IOException("伺服器回傳空回應")
        tokenManager.saveTokens(body.accessToken, body.refreshToken)
        val user = body.user.toDomain()
        cachedUser = user
        Timber.d("Token refreshed: userId=${user.id}")
        user
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        runCatching { apiService.logout() } // 伺服器撤銷失敗時仍繼續清除本地資料
        tokenManager.clearTokens()
        cachedUser = null
        Timber.d("Logged out")
    }

    override fun getCachedUser(): User? = cachedUser
}
