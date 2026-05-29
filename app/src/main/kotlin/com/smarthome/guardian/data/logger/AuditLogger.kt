package com.smarthome.guardian.data.logger

import android.os.Build
import com.google.gson.Gson
import java.util.Base64
import com.smarthome.guardian.data.local.database.AuditDao
import com.smarthome.guardian.data.local.database.entity.AuditLogEntity
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.security.HmacSigner
import com.smarthome.guardian.security.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全 APP 共用的稽核日誌記錄器（Singleton，由 Hilt 注入全域）。
 *
 * ## 功能
 * - **自動填充**：userId（解析 JWT `sub` claim）、timestamp、deviceFingerprint
 * - **HMAC-SHA256 簽章**：每筆日誌在入隊前計算，防止後續竄改（OWASP M10）
 * - **批次寫入**：每 [BATCH_INTERVAL_MS] 毫秒執行一次 Room `@Transaction` INSERT，
 *   降低頻繁 IO 對 UI 執行緒的影響
 * - **非阻塞**：[log] 呼叫即時回傳，實際 DB 寫入於背景 Coroutine 完成
 *
 * ## 使用方式
 * ```kotlin
 * @Inject lateinit var auditLogger: AuditLogger
 *
 * auditLogger.log(AuditAction.LOGIN_SUCCESS, targetId = userId)
 * auditLogger.log(AuditAction.DEVICE_CONTROL, targetId = deviceId, before = oldState, after = newState)
 * ```
 *
 * ## 安全注意事項
 * - `before`/`after` 物件會被序列化為 JSON；請確保不包含明文密碼或 Token
 * - 若 HMAC 金鑰遺失（Keystore 資料抹除），簽章計算失敗時仍會寫入日誌，但 signature 欄位為空
 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditDao: AuditDao,
    private val hmacSigner: HmacSigner,
    private val tokenManager: TokenManager,
) {
    private val scope             = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingLogs       = mutableListOf<AuditLog>()
    private val lock              = Any()
    private val deviceFingerprint = (Build.FINGERPRINT ?: "unknown").take(64)
    private val gson              = Gson()

    init {
        startBatchProcessor()
    }

    // ── 公開 API ──────────────────────────────────────────────────────────────

    /**
     * 記錄一筆稽核事件（非阻塞 fire-and-forget）。
     *
     * 實際 DB 寫入由背景批次處理器在 [BATCH_INTERVAL_MS] 毫秒後執行。
     *
     * @param action   操作類型
     * @param targetId 目標 ID（設備/用戶，可為 null）
     * @param before   操作前的狀態物件（序列化為 JSON，不可含敏感資訊）
     * @param after    操作後的狀態物件（序列化為 JSON）
     */
    fun log(
        action: AuditAction,
        targetId: String? = null,
        before: Any?      = null,
        after: Any?       = null,
    ) {
        try {
            val userId   = runCatching { extractUserId() }.getOrNull() ?: "anonymous"
            val unsigned = AuditLog(
                id                = UUID.randomUUID().toString(),
                userId            = userId,
                action            = action,
                targetId          = targetId,
                before            = before?.let { gson.toJson(it) },
                after             = after?.let { gson.toJson(it) },
                ipAddress         = "127.0.0.1",
                deviceFingerprint = deviceFingerprint,
                timestamp         = System.currentTimeMillis(),
                signature         = "",
            )
            val signature = runCatching {
                hmacSigner.sign(unsigned.toSignableString())
            }.getOrElse { e ->
                Timber.e(e, "AuditLogger: HMAC sign failed for action=$action")
                ""
            }
            synchronized(lock) { pendingLogs.add(unsigned.copy(signature = signature)) }
        } catch (e: Exception) {
            Timber.e(e, "AuditLogger: failed to enqueue log [action=$action]")
        }
    }

    /**
     * 強制立即 flush 所有待寫入的日誌（APP 進入背景或退出前呼叫）。
     * 此函數會阻塞直到 flush 完成。
     */
    suspend fun flush() {
        val pending = synchronized(lock) { pendingLogs.toList().also { pendingLogs.clear() } }
        if (pending.isNotEmpty()) writeBatch(pending)
    }

    /** 取消背景 Coroutine（由 Application 的 onTerminate 呼叫）。 */
    fun destroy() = scope.cancel()

    // ── 私有 ──────────────────────────────────────────────────────────────────

    private fun startBatchProcessor() {
        scope.launch {
            while (isActive) {
                delay(BATCH_INTERVAL_MS)
                flush()
            }
        }
    }

    private suspend fun writeBatch(logs: List<AuditLog>) =
        runCatching {
            auditDao.insertAll(logs.map { AuditLogEntity.fromDomain(it) })
            Timber.d("AuditLogger: persisted ${logs.size} log(s)")
        }.onFailure { e ->
            Timber.e(e, "AuditLogger: batch write failed — ${logs.size} record(s) lost")
        }

    /**
     * 從 JWT access token 的 `sub` claim 解析用戶 ID。
     * 若 Token 不存在或格式異常，回傳 null（由呼叫端替換為 "anonymous"）。
     */
    private fun extractUserId(): String? = runCatching {
        val token = tokenManager.getAccessToken() ?: return null
        val parts = token.split(".")
        if (parts.size != 3) return null
        // Base64URL → Standard Base64（補 padding）
        val padded = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val payload = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        Regex(""""sub"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.get(1)
    }.getOrNull()

    companion object {
        /** 批次寫入間隔（毫秒）。每秒最多執行一次 DB INSERT。 */
        private const val BATCH_INTERVAL_MS = 1_000L
    }
}
