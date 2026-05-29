package com.smarthome.guardian.di

import com.google.gson.Gson
import com.smarthome.guardian.data.remote.dto.LoginResponse
import com.smarthome.guardian.data.remote.dto.RefreshTokenRequest
import com.smarthome.guardian.data.remote.network.AuthEventBus
import com.smarthome.guardian.security.TokenManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp 攔截器：自動管理 JWT Bearer Token 的附加與刷新。
 *
 * ## 流程
 * 1. 在每個非認證端點的請求中附加 `Authorization: Bearer <access_token>`
 * 2. 收到 HTTP 401 時：
 *    a. 以 `synchronized(refreshLock)` 確保同時只有一個執行緒執行刷新
 *    b. 若 Token 在等待鎖的期間已被其他執行緒更新，直接用新 Token 重試
 *    c. 否則以 Refresh Token 呼叫刷新端點（使用獨立的 `refreshClient`，不經過本攔截器）
 *    d. 刷新成功：儲存新 Token，以新 Token 重試原始請求
 *    e. 刷新失敗：透過 [AuthEventBus] 發出強制登出事件
 *
 * ## 循環相依解決方式
 * 刷新呼叫使用 `@Named("refresh")` 的獨立 OkHttpClient（不含本攔截器），
 * 避免 NetworkModule → AuthInterceptor → ApiService → NetworkModule 的循環。
 *
 * ## OWASP M9 — 不安全的認證
 * Token 以 EncryptedSharedPreferences 儲存；攔截器從不在日誌中輸出完整 Token。
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val authEventBus: AuthEventBus,
    @Named("refresh") private val refreshClient: OkHttpClient,
) : Interceptor {

    private val refreshLock  = Object()
    private val gson         = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 這些路徑不附加 Authorization header，避免循環。 */
    private val noAuthPaths  = setOf(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/biometric-register",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (noAuthPaths.any { originalRequest.url.encodedPath.endsWith(it) }) {
            return chain.proceed(originalRequest)
        }

        val token = tokenManager.getAccessToken()
        val response = chain.proceed(originalRequest.withBearer(token))

        if (response.code != 401) return response

        // ── 401：嘗試刷新 Token ───────────────────────────────────────────────
        response.close()

        synchronized(refreshLock) {
            // 若其他執行緒在本執行緒等待期間已刷新，直接重試
            val latestToken = tokenManager.getAccessToken()
            if (latestToken != null && latestToken != token) {
                Timber.d("AuthInterceptor: token refreshed by another thread, retrying")
                return chain.proceed(originalRequest.withBearer(latestToken))
            }

            // 自行執行刷新
            val refreshed = performRefresh()
            return if (refreshed) {
                Timber.d("AuthInterceptor: token refreshed successfully, retrying request")
                chain.proceed(originalRequest.withBearer(tokenManager.getAccessToken()))
            } else {
                Timber.w("AuthInterceptor: token refresh failed — forcing logout")
                authEventBus.emitLogout("Token 刷新失敗，請重新登入")
                // 回傳 401 讓呼叫端感知，UI 層收到 forceLogout 後處理導航
                chain.proceed(originalRequest)
            }
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /**
     * 同步呼叫刷新端點並儲存新 Token。
     * 使用獨立的 `refreshClient`（不含本攔截器），避免循環呼叫。
     *
     * @return `true` 表示刷新成功並已儲存新 Token
     */
    private fun performRefresh(): Boolean {
        val refreshToken = tokenManager.getRefreshToken()
            ?: return false.also { Timber.w("AuthInterceptor: no refresh token available") }

        return runCatching {
            val body    = gson.toJson(RefreshTokenRequest(refreshToken)).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(REFRESH_URL)
                .post(body)
                .build()

            val response = refreshClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("AuthInterceptor: refresh endpoint returned ${response.code}")
                return false
            }

            val responseBody = response.body?.string()
                ?: return false.also { Timber.w("AuthInterceptor: empty refresh response") }

            val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
            tokenManager.saveTokens(loginResponse.accessToken, loginResponse.refreshToken)
            true
        }.getOrElse { e ->
            Timber.e(e, "AuthInterceptor: refresh call threw exception")
            false
        }
    }

    private fun Request.withBearer(token: String?): Request {
        if (token.isNullOrBlank()) return this
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    companion object {
        private const val REFRESH_URL = "${com.smarthome.guardian.BuildConfig.API_BASE_URL}api/v1/auth/refresh"
    }
}
