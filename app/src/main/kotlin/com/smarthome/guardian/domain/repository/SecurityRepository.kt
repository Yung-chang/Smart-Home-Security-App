package com.smarthome.guardian.domain.repository

import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.SecurityLevel
import kotlinx.coroutines.flow.Flow

/**
 * 安全監控的 Repository 介面。
 */
interface SecurityRepository {

    /** 訂閱即時安全等級（WebSocket 推送或本地 AlertEngine 計算）。 */
    fun getSecurityLevel(): Flow<SecurityLevel>

    /** 訂閱所有未確認的警報（含歷史）。 */
    fun getAlerts(): Flow<List<SecurityAlert>>

    /** 取得最新 N 筆警報（儀表板底部預覽用）。 */
    fun getRecentAlerts(limit: Int = 3): Flow<List<SecurityAlert>>

    /** 確認（ACK）指定警報。 */
    suspend fun acknowledgeAlert(alertId: String): Result<Unit>

    /** 批次確認多筆警報。 */
    suspend fun bulkAcknowledge(alertIds: List<String>): Result<Unit>

    /** 未確認警報數量（用於 AppBar Badge）。 */
    fun getUnreadAlertCount(): Flow<Int>
}
