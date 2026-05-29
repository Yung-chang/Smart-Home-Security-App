package com.smarthome.guardian.debug

import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.data.local.database.AlertDao
import com.smarthome.guardian.data.local.database.DeviceDao
import com.smarthome.guardian.data.local.database.entity.AlertEntity
import com.smarthome.guardian.data.local.database.entity.DeviceEntity
import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.domain.model.DeviceOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 無後端情況下的完整設備模擬器（僅 Debug build 生效）。
 *
 * ## 功能
 * - [seedIfEmpty]：首次啟動（或版本升級）時向 Room DB 植入 15 個示範設備 + 歷史警報
 * - [handleCommand]：指令直接更新本地 DB，Room Flow 自動通知所有訂閱者（跨畫面同步）
 * - [startSimulation]：每 15 秒模擬一次，包含：
 *     - 感應器隨機觸發 → 自動產生安全警報
 *     - 離線設備 + 低電量 → 週期性警報
 *     - 隨機失敗解鎖事件
 *     - 電池消耗 + 訊號波動
 */
@Singleton
class DemoSimulator @Inject constructor(
    private val deviceDao: DeviceDao,
    private val alertDao: AlertDao,
    private val securePreferences: SecurePreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 遞增此版本號即可讓 APP 啟動時自動清空並重刷所有示範資料
    private val DEMO_VERSION = "4"

    // 防止重複警報：記錄每個 deviceId 最後一次產生警報的時間
    private val lastAlertMs = mutableMapOf<String, Long>()

    // ── 初始化 ────────────────────────────────────────────────────────────────

    suspend fun seedIfEmpty() {
        if (!BuildConfig.DEBUG) return
        if (securePreferences.getDemoDataVersion() != DEMO_VERSION) {
            deviceDao.deleteAll()
            alertDao.deleteAll()
            deviceDao.upsertAll(demoDevices())
            alertDao.upsertAll(historicalAlerts())
            securePreferences.saveDemoDataVersion(DEMO_VERSION)
            Timber.d("DemoSimulator: re-seeded v$DEMO_VERSION")
        } else {
            if (deviceDao.count() == 0) deviceDao.upsertAll(demoDevices())
            if (alertDao.count() == 0)  alertDao.upsertAll(historicalAlerts())
        }
    }

    fun startSimulation() {
        if (!BuildConfig.DEBUG) return
        scope.launch {
            while (true) {
                delay(15_000L)   // 每 15 秒 tick 一次
                simulateTick()
            }
        }
    }

    // ── 指令處理（Room Flow 自動通知所有訂閱畫面）────────────────────────────

    suspend fun handleCommand(deviceId: String, operation: DeviceOperation): Result<Unit> = runCatching {
        val device  = deviceDao.getById(deviceId) ?: return@runCatching
        val now     = System.currentTimeMillis()
        // DoorLockPanel 定義：isOn=true → 解鎖，isOn=false → 上鎖
        val updated = when (operation) {
            DeviceOperation.TOGGLE_ON    -> device.copy(isOn = true,         status = "ONLINE", lastSeen = now)
            DeviceOperation.TOGGLE_OFF   -> device.copy(isOn = false,        status = "ONLINE", lastSeen = now)
            DeviceOperation.TOGGLE       -> device.copy(isOn = !device.isOn, status = "ONLINE", lastSeen = now)
            DeviceOperation.LOCK         -> device.copy(isOn = false,        status = "ONLINE", lastSeen = now)
            DeviceOperation.UNLOCK       -> device.copy(isOn = true,         status = "ONLINE", lastSeen = now)
            DeviceOperation.RESET_ALARM  -> device.copy(isOn = false,        lastSeen = now)
            else                         -> device.copy(lastSeen = now)
        }
        deviceDao.upsert(updated)
        Timber.d("DemoSimulator: $operation → ${device.name} isOn=${updated.isOn}")
    }

    // ── 週期模擬（每 15 秒）──────────────────────────────────────────────────

    private suspend fun simulateTick() = runCatching {
        val all = deviceDao.getAllOnce()
        val now = System.currentTimeMillis()

        all.forEach { device ->
            var d = device.copy(lastSeen = now)

            // 電池：OFFLINE 消耗快（30% 機率），ONLINE 較慢（10%）
            d.batteryLevel?.let { bat ->
                val drain = if (device.status == "OFFLINE") 0.3 else 0.1
                if (Math.random() < drain && bat > 1) d = d.copy(batteryLevel = bat - 1)
            }

            // 訊號波動 ±3
            d.signalStrength?.let { sig ->
                val delta = (Math.random() * 7 - 3).toInt()
                d = d.copy(signalStrength = (sig + delta).coerceIn(20, 100))
            }

            // 感應器觸發模擬
            if (device.type == "SENSOR_MOTION" && device.status == "ONLINE" && Math.random() < 0.15) {
                d = d.copy(isOn = true)
                generateAlert(
                    deviceId = device.id,
                    type     = "INTRUSION",
                    severity = "LOW",
                    message  = "【${device.name}】偵測到移動",
                    cooldownMs = 2 * 60_000L,
                )
                scope.launch { delay(5_000L); runCatching { deviceDao.upsert(d.copy(isOn = false)) } }
            }
            if (device.type == "SENSOR_DOOR" && device.status == "ONLINE" && Math.random() < 0.08) {
                d = d.copy(isOn = !device.isOn)
                generateAlert(
                    deviceId = device.id,
                    type     = "INTRUSION",
                    severity = "LOW",
                    message  = "【${device.name}】狀態變更：${if (!device.isOn) "開啟" else "關閉"}",
                    cooldownMs = 3 * 60_000L,
                )
            }

            // 離線設備每 5 分鐘產生一次警報
            if (device.status == "OFFLINE") {
                generateAlert(
                    deviceId   = device.id,
                    type       = "DEVICE_OFFLINE",
                    severity   = "MEDIUM",
                    message    = "【${device.name}】連線中斷，請檢查設備電源",
                    cooldownMs = 5 * 60_000L,
                )
            }

            // 低電量警報（電量 < 20%，每 10 分鐘一次）
            d.batteryLevel?.let { bat ->
                if (bat in 1..19) {
                    generateAlert(
                        deviceId   = device.id + "_battery",
                        type       = "SYSTEM",
                        severity   = "LOW",
                        message    = "【${device.name}】電量不足 $bat%，請盡快更換電池",
                        cooldownMs = 10 * 60_000L,
                    )
                }
            }

            // d 一定與 device 不同（lastSeen 已更新），直接 upsert
            deviceDao.upsert(d)
        }

        // 自動清理：已確認且超過 2 小時的舊警報（未確認的保留直到用戶手動處理）
        alertDao.deleteAcknowledgedOlderThan(now - 2 * 3600_000L)

        // 隨機產生失敗解鎖嘗試（整體 3% 機率）
        if (Math.random() < 0.03) {
            val lock = all.firstOrNull { it.type == "DOOR_LOCK" && it.status == "ONLINE" }
            lock?.let {
                generateAlert(
                    deviceId   = it.id + "_auth",
                    type       = "AUTH_FAIL",
                    severity   = "MEDIUM",
                    message    = "【${it.name}】偵測到未授權解鎖嘗試",
                    cooldownMs = 5 * 60_000L,
                )
            }
        }
    }.onFailure { Timber.e(it, "DemoSimulator tick error") }

    // ── 警報產生（帶冷卻時間，避免重複洗版）────────────────────────────────

    private suspend fun generateAlert(
        deviceId: String, type: String, severity: String,
        message: String, cooldownMs: Long,
    ) {
        val now  = System.currentTimeMillis()
        val last = lastAlertMs[deviceId] ?: 0L
        if (now - last < cooldownMs) return

        val alert = AlertEntity(
            id             = UUID.randomUUID().toString(),
            type           = type,
            severity       = severity,
            deviceId       = deviceId.removeSuffix("_battery").removeSuffix("_auth"),
            message        = message,
            timestamp      = now,
            isAcknowledged = false,
            actionTaken    = null,
        )
        alertDao.upsert(alert)
        lastAlertMs[deviceId] = now
        Timber.d("DemoSimulator: alert [$severity] $message")
    }

    // ── 歷史警報種子資料（APP 安裝後即可見豐富的事件記錄）────────────────────

    private fun historicalAlerts(): List<AlertEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            // 已確認的歷史事件
            alert("h1", "INTRUSION",    "LOW",    "demo-motion-002", "【廚房移動感應器】偵測到移動",             now - 4 * 3600_000, ack = true),
            alert("h2", "INTRUSION",    "LOW",    "demo-motion-001", "【客廳移動感應器】偵測到移動",             now - 3 * 3600_000, ack = true),
            alert("h3", "INTRUSION",    "LOW",    "demo-camera-001", "【玄關攝影機】偵測到移動物體",             now - 3600_000,     ack = true),
            alert("h4", "AUTH_FAIL",    "MEDIUM", "demo-lock-001",   "【大門智慧門鎖】偵測到未授權解鎖嘗試",     now - 1800_000,     ack = true),
            // 未確認的警報 → 影響安全分數 + 威脅地圖顯色
            alert("h5", "DEVICE_OFFLINE","MEDIUM","demo-lock-002",   "【後門門鎖】連線中斷，請檢查設備電源",     now - 2 * 3600_000, ack = false),
            alert("h6", "SYSTEM",       "LOW",    "demo-lock-002",   "【後門門鎖】電量不足 12%，請盡快更換電池", now - 1500_000,     ack = false),
            alert("h7", "INTRUSION",    "LOW",    "demo-sensor-001", "【臥室門窗感應器】狀態變更：開啟",         now - 900_000,      ack = false),
        )
    }

    private fun alert(
        id: String, type: String, severity: String,
        deviceId: String, message: String, timestamp: Long, ack: Boolean,
    ) = AlertEntity(
        id             = id,
        type           = type,
        severity       = severity,
        deviceId       = deviceId,
        message        = message,
        timestamp      = timestamp,
        isAcknowledged = ack,
        actionTaken    = null,
    )

    // ── 示範設備定義 ──────────────────────────────────────────────────────────

    private fun demoDevices(): List<DeviceEntity> = listOf(
        // ── 客廳 ──────────────────────────────────────────────────────────────
        dev("demo-light-001",  "客廳主燈",       "LIGHT",          "living_room", "ONLINE",  isOn = true,  fw = "2.1.3", signal = 90),
        dev("demo-light-002",  "電視燈帶",       "LIGHT",          "living_room", "ONLINE",  isOn = false, fw = "1.5.0", signal = 85),
        dev("demo-thermo-001", "客廳溫控器",     "THERMOSTAT",     "living_room", "ONLINE",  fw = "3.0.1", signal = 88),
        dev("demo-motion-001", "客廳移動感應器", "SENSOR_MOTION",  "living_room", "ONLINE",  bat = 78,     signal = 72),
        // ── 臥室 ──────────────────────────────────────────────────────────────
        dev("demo-light-003",  "主臥燈",         "LIGHT",          "bedroom",     "ONLINE",  isOn = false, fw = "2.0.1", signal = 75),
        dev("demo-sensor-001", "臥室門窗感應器", "SENSOR_DOOR",    "bedroom",     "ONLINE",  isOn = false, bat = 92,     signal = 68),
        dev("demo-outlet-001", "臥室智慧插座",   "OUTLET",         "bedroom",     "ONLINE",  isOn = false, signal = 80),
        // ── 廚房 ──────────────────────────────────────────────────────────────
        dev("demo-light-004",  "廚房燈",         "LIGHT",          "kitchen",     "ONLINE",  isOn = true,  fw = "1.8.0", signal = 70),
        dev("demo-outlet-002", "廚房智慧插座",   "OUTLET",         "kitchen",     "ONLINE",  isOn = true,  signal = 73),
        dev("demo-motion-002", "廚房移動感應器", "SENSOR_MOTION",  "kitchen",     "ONLINE",  bat = 91,     signal = 77),
        // ── 門口 ──────────────────────────────────────────────────────────────
        dev("demo-lock-001",   "大門智慧門鎖",   "DOOR_LOCK",      "entrance",    "ONLINE",  isOn = false, fw = "3.2.1", bat = 65, signal = 95),
        dev("demo-camera-001", "玄關攝影機",     "CAMERA",         "entrance",    "ONLINE",  fw = "1.8.2", signal = 91),
        dev("demo-light-005",  "玄關燈",         "LIGHT",          "entrance",    "ONLINE",  isOn = false, fw = "1.2.0", signal = 88),
        dev("demo-lock-002",   "後門門鎖",       "DOOR_LOCK",      "entrance",    "OFFLINE", isOn = false, fw = "2.1.0", bat = 12, signal = 45),
        dev("demo-alarm-001",  "全屋警報器",     "ALARM",          "entrance",    "ONLINE",  isOn = false, fw = "2.5.0", signal = 93),
    )

    private fun dev(
        id: String, name: String, type: String,
        roomId: String, status: String,
        isOn: Boolean = false, fw: String = "1.0.0",
        bat: Int? = null, signal: Int? = null,
    ) = DeviceEntity(
        id             = id, name = name, type = type,
        roomId         = roomId, status = status,
        isOn           = isOn, firmware = fw,
        batteryLevel   = bat, signalStrength = signal,
        macAddress     = fakeMac(id),
        lastSeen       = System.currentTimeMillis(),
    )

    private fun fakeMac(seed: String): String {
        val h = seed.hashCode()
        return "A2:%02X:%02X:%02X:%02X:%02X".format(
            (h shr 20) and 0xFF, (h shr 16) and 0xFF,
            (h shr 12) and 0xFF, (h shr 8)  and 0xFF,
            h and 0xFF,
        )
    }
}
