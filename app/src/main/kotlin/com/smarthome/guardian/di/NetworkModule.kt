package com.smarthome.guardian.di

import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.data.remote.api.ApiService
import com.smarthome.guardian.security.CertificatePinner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 提供所有網路相關的 Singleton：
 * - 主 [OkHttpClient]（含 CertificatePinner + AuthInterceptor + 日誌）
 * - 刷新專用 [OkHttpClient]（`"refresh"`，不含 AuthInterceptor，避免循環）
 * - WebSocket 專用 [OkHttpClient]（`"websocket"`，長連線 + 無讀取逾時）
 * - [Retrofit] + [ApiService]
 *
 * ## TLS 1.3
 * OkHttp 4.x 預設啟用 TLS 1.3；Production 環境透過 [CertificatePinner] 強制憑證 pinning。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S    = 30L
    private const val WRITE_TIMEOUT_S   = 30L
    private const val WS_PING_INTERVAL_S= 30L   // OkHttp 內建 WebSocket ping

    // ── OkHttpClient（主請求） ─────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        certificatePinner: CertificatePinner,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .certificatePinner(certificatePinner.build())
        .addInterceptor(authInterceptor)
        .applyDebugLogging()
        .build()

    // ── OkHttpClient（Token 刷新，無 AuthInterceptor）─────────────────────────

    /**
     * 專供 [AuthInterceptor] 呼叫刷新端點使用。
     * 不含 [AuthInterceptor]，避免 401 → refresh → 401 的無限循環。
     */
    @Provides
    @Singleton
    @Named("refresh")
    fun provideRefreshOkHttpClient(
        certificatePinner: CertificatePinner,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .certificatePinner(certificatePinner.build())
        .applyDebugLogging()
        .build()

    // ── OkHttpClient（WebSocket） ─────────────────────────────────────────────

    /**
     * WebSocket 專用 Client：
     * - `readTimeout = 0`：WSS 為長連線，禁止逾時關閉
     * - `pingInterval`：OkHttp 自動傳送 WebSocket ping（作為輔助，Manager 另有應用層心跳）
     */
    @Provides
    @Singleton
    @Named("websocket")
    fun provideWebSocketOkHttpClient(
        authInterceptor: AuthInterceptor,
        certificatePinner: CertificatePinner,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)           // 長連線不設讀取逾時
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .pingInterval(WS_PING_INTERVAL_S, TimeUnit.SECONDS)
        .certificatePinner(certificatePinner.build())
        .addInterceptor(authInterceptor)
        .applyDebugLogging()
        .build()

    // ── Retrofit + ApiService ─────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    // ── 私有擴充 ──────────────────────────────────────────────────────────────

    private fun OkHttpClient.Builder.applyDebugLogging(): OkHttpClient.Builder = apply {
        if (BuildConfig.DEBUG) {
            addNetworkInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
            )
        }
    }
}
