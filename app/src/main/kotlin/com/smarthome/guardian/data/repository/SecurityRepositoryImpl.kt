package com.smarthome.guardian.data.repository

import com.smarthome.guardian.data.local.database.AlertDao
import com.smarthome.guardian.data.local.database.entity.AlertEntity
import com.smarthome.guardian.data.remote.api.ApiService
import com.smarthome.guardian.data.remote.dto.AcknowledgeRequest
import com.smarthome.guardian.data.remote.websocket.WebSocketManager
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.SecurityLevel
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.domain.repository.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SecurityRepository] 的完整實作（PROMPT 09 接入 WebSocket + REST + Room）。
 *
 * ## 資料流策略
 * - **即時警報**：WebSocket 推送 → 寫入 Room AlertDao → Room Flow 廣播至 UI
 * - **歷史警報**：初次啟動時從 REST API 拉取最新 50 筆，覆寫本地快取
 * - **確認操作**：先更新 Room 本地狀態（即時反映 UI），再非同步同步至伺服器
 *
 * ## 安全等級計算
 * 從未確認警報自動推算：有 CRITICAL → ALERT，有 HIGH → WARNING，否則 SECURE。
 */
@Singleton
class SecurityRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val alertDao: AlertDao,
    private val webSocketManager: WebSocketManager,
) : SecurityRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 從 Room 讀取的全部警報（離線優先，WebSocket 更新後自動刷新）。 */
    private val allAlerts: Flow<List<SecurityAlert>> =
        alertDao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** 從警報列表推算安全等級（StateFlow，供 Dashboard AppBar 訂閱）。 */
    private val securityLevel: Flow<SecurityLevel> = allAlerts.map { alerts ->
        val unread = alerts.filter { !it.isAcknowledged }
        when {
            unread.any { it.severity == Severity.CRITICAL } -> SecurityLevel.ALERT
            unread.any { it.severity == Severity.HIGH }     -> SecurityLevel.WARNING
            unread.isNotEmpty()                             -> SecurityLevel.WARNING
            else                                            -> SecurityLevel.SECURE
        }
    }

    init {
        subscribeToWebSocketAlerts()
        fetchRecentAlertsFromApi()
    }

    // ── SecurityRepository 介面 ───────────────────────────────────────────────

    override fun getSecurityLevel(): Flow<SecurityLevel> = securityLevel

    override fun getAlerts(): Flow<List<SecurityAlert>> = allAlerts

    override fun getRecentAlerts(limit: Int): Flow<List<SecurityAlert>> =
        alertDao.getRecent(limit).map { entities -> entities.map { it.toDomain() } }

    override fun getUnreadAlertCount(): Flow<Int> = alertDao.getUnreadCount()

    override suspend fun acknowledgeAlert(alertId: String): Result<Unit> = runCatching {
        // 先更新本地 DB（即時 UI 反映）
        alertDao.acknowledge(alertId)
        // 非同步同步至伺服器（失敗不阻塞 UI，下次開啟 APP 時重新拉取）
        runCatching {
            val response = apiService.acknowledgeAlert(alertId)
            if (!response.isSuccessful) Timber.w("ACK alert $alertId failed: HTTP ${response.code()}")
        }.onFailure { e -> Timber.e(e, "ACK alert $alertId server sync failed") }
    }

    override suspend fun bulkAcknowledge(alertIds: List<String>): Result<Unit> = runCatching {
        alertDao.acknowledgeAll(alertIds)
        runCatching {
            val response = apiService.bulkAcknowledgeAlerts(AcknowledgeRequest(alertIds))
            if (!response.isSuccessful) Timber.w("Bulk ACK failed: HTTP ${response.code()}")
        }.onFailure { e -> Timber.e(e, "Bulk ACK server sync failed") }
    }

    // ── 私有：WebSocket 訂閱 ──────────────────────────────────────────────────

    private fun subscribeToWebSocketAlerts() {
        scope.launch {
            webSocketManager.observeAlerts()
                .catch { e -> Timber.e(e, "SecurityRepo: WS alert flow error") }
                .collect { alert ->
                    alertDao.upsert(AlertEntity.fromDomain(alert))
                    Timber.d("New WebSocket alert: ${alert.type} severity=${alert.severity}")
                }
        }
    }

    /** 啟動時從 REST API 拉取最近 50 筆警報作為本地快取基底。 */
    private fun fetchRecentAlertsFromApi() {
        scope.launch {
            runCatching {
                val response = apiService.getAlerts(limit = 50)
                if (response.isSuccessful) {
                    val alerts = response.body()?.alerts?.map { it.toDomain() } ?: emptyList()
                    alertDao.upsertAll(alerts.map { AlertEntity.fromDomain(it) })
                    Timber.d("SecurityRepo: cached ${alerts.size} alerts from API")
                }
            }.onFailure { e ->
                Timber.w(e, "SecurityRepo: initial alert fetch failed (offline?)")
            }
        }
    }
}
