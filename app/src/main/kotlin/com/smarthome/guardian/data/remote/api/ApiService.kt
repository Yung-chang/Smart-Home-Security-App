package com.smarthome.guardian.data.remote.api

import com.google.gson.annotations.SerializedName
import com.smarthome.guardian.data.remote.dto.AcknowledgeRequest
import com.smarthome.guardian.data.remote.dto.AlertListResponse
import com.smarthome.guardian.data.remote.dto.BiometricRegisterRequest
import com.smarthome.guardian.data.remote.dto.DeviceCommandRequest
import com.smarthome.guardian.data.remote.dto.DeviceListResponse
import com.smarthome.guardian.data.remote.dto.DeviceResponse
import com.smarthome.guardian.data.remote.dto.DeviceSettingsRequest
import com.smarthome.guardian.data.remote.dto.LoginRequest
import com.smarthome.guardian.data.remote.dto.LoginResponse
import com.smarthome.guardian.data.remote.dto.RefreshTokenRequest
import com.smarthome.guardian.data.remote.dto.VerifyPinRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API 介面（PROMPT 09 完整版）。
 *
 * 所有請求透過 TLS 1.3 傳輸，[com.smarthome.guardian.di.AuthInterceptor]
 * 自動附加 Bearer Token（認證端點除外）。
 *
 * 錯誤回應統一由 Repository 層轉為 [Result]，ViewModel 不直接接觸 HTTP 細節。
 */
interface ApiService {

    // ── 認證 ──────────────────────────────────────────────────────────────────

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/v1/auth/biometric-register")
    suspend fun registerBiometric(@Body request: BiometricRegisterRequest): Response<Unit>

    @POST("api/v1/auth/verify-pin")
    suspend fun verifyPin(@Body request: VerifyPinRequest): Response<LoginResponse>

    // ── 設備 ──────────────────────────────────────────────────────────────────

    @GET("api/v1/devices")
    suspend fun getDevices(
        @Query("room_id") roomId: String? = null,
    ): Response<DeviceListResponse>

    @GET("api/v1/devices/{deviceId}")
    suspend fun getDevice(
        @Path("deviceId") deviceId: String,
    ): Response<DeviceResponse>

    /**
     * 發送設備控制指令。
     * Request body 含 HMAC-SHA256 簽章，伺服器驗證後才執行。
     */
    @POST("api/v1/devices/{deviceId}/commands")
    suspend fun sendCommand(
        @Path("deviceId") deviceId: String,
        @Body command: DeviceCommandRequest,
    ): Response<Unit>

    @PATCH("api/v1/devices/{deviceId}/settings")
    suspend fun updateDeviceSettings(
        @Path("deviceId") deviceId: String,
        @Body settings: DeviceSettingsRequest,
    ): Response<DeviceResponse>

    // ── 安全警報 ──────────────────────────────────────────────────────────────

    @GET("api/v1/alerts")
    suspend fun getAlerts(
        @Query("limit")      limit: Int = 50,
        @Query("offset")     offset: Int = 0,
        @Query("severity")   severity: String? = null,
        @Query("start_time") startTime: Long? = null,
        @Query("end_time")   endTime: Long? = null,
    ): Response<AlertListResponse>

    @POST("api/v1/alerts/{alertId}/acknowledge")
    suspend fun acknowledgeAlert(
        @Path("alertId") alertId: String,
    ): Response<Unit>

    @POST("api/v1/alerts/acknowledge/bulk")
    suspend fun bulkAcknowledgeAlerts(
        @Body request: AcknowledgeRequest,
    ): Response<Unit>

    // ── WebSocket Token ───────────────────────────────────────────────────────

    /** 取得短效 WebSocket Token（15 分鐘），避免 WS URL 暴露長效 JWT。 */
    @POST("api/v1/ws/token")
    suspend fun getWebSocketToken(): Response<WsTokenResponse>
}

data class WsTokenResponse(
    @SerializedName("ws_token")   val wsToken: String,
    @SerializedName("ws_url")     val wsUrl: String,
    @SerializedName("expires_in") val expiresIn: Int = 900,
)
