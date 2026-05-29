package com.smarthome.guardian.security.engine

import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.domain.repository.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地警報規則引擎，即使 APP 在背景（由 [SecurityService] 持有）仍可執行。
 *
 * ## 規則清單
 * | # | 規則 | 觸發條件 |
 * |---|------|---------|
 * | 1 | 設備離線 | 設備上次回報 > [OFFLINE_THRESHOLD_MINUTES] 分鐘 |
 * | 2 | 重複操作失敗 | 同設備 [FAILURE_WINDOW_MS] 內失敗 > [FAILURE_COUNT_THRESHOLD] 次 |
 * | 3 | 非授權時間門鎖操作 | 凌晨 [ALERT_HOUR_START]–[ALERT_HOUR_END] 偵測到門鎖指令 |
 * | 4 | Token 刷新失敗過多 | 連續失敗 >= [TOKEN_FAIL_THRESHOLD] 次 |
 *
 * 每條規則觸發後：
 * 1. 寫入 [SecurityRepository]（更新本地快取）
 * 2. 透過 [alertFlow] 廣播給 UI
 * 3. 透過 NotificationHelper 發送本地通知（完整實作於 PROMPT 12）
 */
@Singleton
class AlertEngine @Inject constructor(
    private val securityRepository: SecurityRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _alertFlow = MutableSharedFlow<SecurityAlert>(extraBufferCapacity = 32)
    val alertFlow: SharedFlow<SecurityAlert> = _alertFlow.asSharedFlow()

    // ── 規則 2：失敗次數追蹤 ──────────────────────────────────────────────────
    private val failureTracker = ConcurrentHashMap<String, MutableList<Long>>()

    // ── 規則 4：Token 刷新失敗計數 ────────────────────────────────────────────
    private var tokenRefreshFailCount = 0

    // ── 設備狀態追蹤 ──────────────────────────────────────────────────────────
    private val deviceLastSeen = ConcurrentHashMap<String, Long>()

    // ── 公開 API ──────────────────────────────────────────────────────────────

    /**
     * 接收 MQTT/WebSocket 的設備狀態事件。
     * 由 [SecurityService] 在收到設備上線 ping 時呼叫。
     */
    fun onDeviceHeartbeat(deviceId: String) {
        deviceLastSeen[deviceId] = System.currentTimeMillis()
    }

    /**
     * 接收設備操作失敗事件（如門鎖解鎖失敗、PIN 錯誤）。
     * 觸發規則 2 的計數窗口。
     */
    fun onDeviceOperationFailed(deviceId: String, operation: String) {
        val now = System.currentTimeMillis()
        val timestamps = failureTracker.getOrPut(deviceId) { mutableListOf() }
        timestamps.removeIf { now - it > FAILURE_WINDOW_MS }
        timestamps.add(now)

        if (timestamps.size >= FAILURE_COUNT_THRESHOLD) {
            timestamps.clear()
            triggerRule2(deviceId, operation, timestamps.size)
        }
    }

    /**
     * 接收門鎖操作事件，觸發規則 3（非授權時間偵測）。
     */
    fun onDoorLockOperation(deviceId: String, operation: String) {
        val hour = LocalTime.now().hour
        if (hour in ALERT_HOUR_START until ALERT_HOUR_END) {
            triggerRule3(deviceId, operation, hour)
        }
    }

    /**
     * 通知 Token 刷新失敗（由 AuthInterceptor / AuthRepository 呼叫）。
     * 觸發規則 4 的連續失敗計數。
     */
    fun onTokenRefreshFailed() {
        tokenRefreshFailCount++
        if (tokenRefreshFailCount >= TOKEN_FAIL_THRESHOLD) {
            tokenRefreshFailCount = 0
            triggerRule4()
        }
    }

    /** Token 刷新成功時重置計數。 */
    fun onTokenRefreshSuccess() {
        tokenRefreshFailCount = 0
    }

    /**
     * 定期呼叫（建議每 5 分鐘，由 [SecurityService] 的排程器觸發）。
     * 檢查所有設備是否超過離線閾值（規則 1）。
     */
    fun checkDeviceOffline(allDeviceIds: List<String>) {
        val now = System.currentTimeMillis()
        allDeviceIds.forEach { deviceId ->
            val last = deviceLastSeen[deviceId] ?: return@forEach
            val minutesOffline = (now - last) / 60_000L
            if (minutesOffline > OFFLINE_THRESHOLD_MINUTES) {
                triggerRule1(deviceId, minutesOffline)
                deviceLastSeen[deviceId] = now // 重置，避免重複觸發
            }
        }
    }

    // ── 規則觸發 ──────────────────────────────────────────────────────────────

    /** 規則 1：設備離線超過閾值。 */
    private fun triggerRule1(deviceId: String, minutes: Long) {
        Timber.w("Rule1: Device $deviceId offline for $minutes min")
        emitAlert(
            SecurityAlert(
                id       = UUID.randomUUID().toString(),
                type     = AlertType.DEVICE_OFFLINE,
                severity = Severity.MEDIUM,
                deviceId = deviceId,
                message  = "設備已離線 $minutes 分鐘，請確認電源與網路連線",
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /** 規則 2：短時間內多次操作失敗。 */
    private fun triggerRule2(deviceId: String, operation: String, count: Int) {
        Timber.w("Rule2: Device $deviceId failed $count times in ${FAILURE_WINDOW_MS / 1000}s")
        emitAlert(
            SecurityAlert(
                id       = UUID.randomUUID().toString(),
                type     = AlertType.AUTH_FAIL,
                severity = Severity.HIGH,
                deviceId = deviceId,
                message  = "${FAILURE_WINDOW_MS / 1000} 秒內偵測到 $count 次連續操作失敗（$operation），疑似暴力破解",
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /** 規則 3：非授權時間偵測到門鎖操作。 */
    private fun triggerRule3(deviceId: String, operation: String, hour: Int) {
        Timber.w("Rule3: DoorLock $deviceId operated at hour $hour (unauthorized)")
        emitAlert(
            SecurityAlert(
                id       = UUID.randomUUID().toString(),
                type     = AlertType.INTRUSION,
                severity = Severity.CRITICAL,
                deviceId = deviceId,
                message  = "凌晨 ${hour}:00 偵測到門鎖操作（$operation），非授權時段，請立即確認",
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /** 規則 4：Token 刷新連續失敗，疑似 Token 遭竊。 */
    private fun triggerRule4() {
        Timber.w("Rule4: Token refresh failed $TOKEN_FAIL_THRESHOLD consecutive times")
        emitAlert(
            SecurityAlert(
                id       = UUID.randomUUID().toString(),
                type     = AlertType.ANOMALY,
                severity = Severity.CRITICAL,
                deviceId = null,
                message  = "連續 $TOKEN_FAIL_THRESHOLD 次 Token 刷新失敗，帳號可能遭到盜用，已強制登出",
                timestamp = System.currentTimeMillis(),
                actionTaken = "強制登出所有裝置",
            )
        )
    }

    private fun emitAlert(alert: SecurityAlert) {
        scope.launch {
            _alertFlow.emit(alert)
            // TODO PROMPT 12: NotificationHelper.showAlertNotification(alert)
            // TODO PROMPT 09: (透過 WebSocket/FCM 同步至後端)
        }
    }

    // ── 常數 ──────────────────────────────────────────────────────────────────

    companion object {
        /** 設備被視為離線的分鐘閾值。 */
        const val OFFLINE_THRESHOLD_MINUTES = 10L

        /** 多次失敗計算窗口（毫秒）。 */
        const val FAILURE_WINDOW_MS = 60_000L

        /** 觸發高風險警報的失敗次數。 */
        const val FAILURE_COUNT_THRESHOLD = 5

        /** 凌晨警戒開始時間（inclusive）。 */
        const val ALERT_HOUR_START = 2

        /** 凌晨警戒結束時間（exclusive）。 */
        const val ALERT_HOUR_END = 6

        /** Token 刷新失敗觸發閾值。 */
        const val TOKEN_FAIL_THRESHOLD = 3
    }
}
