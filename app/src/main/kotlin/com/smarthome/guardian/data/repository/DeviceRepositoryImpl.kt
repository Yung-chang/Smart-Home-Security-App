package com.smarthome.guardian.data.repository

import com.smarthome.guardian.data.local.database.DeviceDao
import com.smarthome.guardian.data.local.database.entity.DeviceEntity
import com.smarthome.guardian.data.remote.api.ApiService
import com.smarthome.guardian.data.remote.dto.DeviceCommandRequest
import com.smarthome.guardian.data.remote.dto.DeviceSettingsRequest
import com.smarthome.guardian.data.remote.websocket.WebSocketManager
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceCommand
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.security.HmacSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceRepository] 的完整實作（PROMPT 09 接入 REST + WebSocket + Room）。
 *
 * ## 資料流策略（Offline-First）
 * 1. **讀取**：始終從 Room DB 讀取（[DeviceDao]），確保離線可用
 * 2. **即時更新**：訂閱 [WebSocketManager] 設備狀態事件，即時更新 Room DB
 * 3. **初始載入**：呼叫 [refresh] 從 REST API 拉取設備列表，覆寫本地快取
 *
 * ## 設備指令安全
 * 所有 [sendCommand] 呼叫自動以 HMAC-SHA256 簽章（[HmacSigner]），
 * 伺服器驗證簽章後才執行，防止指令在傳輸途中被竄改（OWASP M5）。
 */
@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val webSocketManager: WebSocketManager,
    private val deviceDao: DeviceDao,
    private val hmacSigner: HmacSigner,
) : DeviceRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        subscribeToWebSocketUpdates()
    }

    // ── 查詢 ──────────────────────────────────────────────────────────────────

    override fun getDevices(): Flow<List<Device>> =
        deviceDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getDevice(id: String): Flow<Device?> =
        deviceDao.getAll().map { entities -> entities.firstOrNull { it.id == id }?.toDomain() }

    override fun getDevicesByRoom(roomId: String): Flow<List<Device>> =
        deviceDao.getByRoom(roomId).map { entities -> entities.map { it.toDomain() } }

    // ── 指令 ──────────────────────────────────────────────────────────────────

    override suspend fun sendCommand(
        deviceId: String,
        operation: DeviceOperation,
        payload: String,
    ): Result<Unit> = runCatching {
        // 1. 建立指令並計算 HMAC 簽章
        val commandId   = UUID.randomUUID().toString()
        val timestamp   = System.currentTimeMillis()
        val signable    = "$commandId|$deviceId|$operation|$payload|$timestamp"
        val signature   = hmacSigner.sign(signable)

        val request = DeviceCommandRequest(
            commandId  = commandId,
            type       = operation.name,
            parameters = if (payload.isNotBlank()) mapOf("payload" to payload) else emptyMap(),
            timestamp  = timestamp,
            signature  = signature,
        )

        // 2. 透過 REST API 發送（確保伺服器驗證後執行）
        val response = apiService.sendCommand(deviceId, request)
        if (!response.isSuccessful) {
            throw RuntimeException("sendCommand failed: HTTP ${response.code()}")
        }
        Timber.d("Command sent: device=$deviceId op=$operation sig=${signature.take(8)}…")
    }

    // ── 設定更新 ──────────────────────────────────────────────────────────────

    override suspend fun updateSettings(device: Device): Result<Unit> = runCatching {
        val request = DeviceSettingsRequest(
            name     = device.name,
            roomId   = device.roomId,
            isLocked = device.isLocked,
        )
        val response = apiService.updateDeviceSettings(device.id, request)
        if (response.isSuccessful) {
            response.body()?.toDomain()?.let { updated ->
                deviceDao.upsert(DeviceEntity.fromDomain(updated))
            }
        } else {
            throw RuntimeException("updateSettings failed: HTTP ${response.code()}")
        }
    }

    // ── 新增（本地暫存） ──────────────────────────────────────────────────────

    override suspend fun addDevice(device: Device): Result<Unit> = runCatching {
        deviceDao.upsert(DeviceEntity.fromDomain(device))
        Timber.d("Device added locally: ${device.name} (${device.type})")
    }

    // ── 刷新（REST → Room） ───────────────────────────────────────────────────

    override suspend fun refresh(): Result<Unit> = runCatching {
        val response = apiService.getDevices()
        if (!response.isSuccessful) {
            throw RuntimeException("refresh failed: HTTP ${response.code()}")
        }
        val devices = response.body()?.devices?.map { it.toDomain() } ?: emptyList()
        deviceDao.upsertAll(devices.map { DeviceEntity.fromDomain(it) })
        Timber.d("Refreshed ${devices.size} devices from API")
    }

    // ── 私有：WebSocket 訂閱 ──────────────────────────────────────────────────

    /**
     * 訂閱 WebSocket 設備狀態更新，即時反映至 Room DB。
     * 使用 [SupervisorJob]，訂閱失敗不影響其他功能。
     */
    private fun subscribeToWebSocketUpdates() {
        scope.launch {
            webSocketManager.observeAllDeviceStatusUpdates()
                .catch { e -> Timber.e(e, "DeviceRepo: WS device status flow error") }
                .collect { (deviceId, status) ->
                    updateDeviceStatusInDb(deviceId, status)
                }
        }
    }

    private suspend fun updateDeviceStatusInDb(deviceId: String, status: DeviceStatus) {
        runCatching {
            val existing = deviceDao.getById(deviceId) ?: return
            deviceDao.upsert(existing.copy(status = status.name))
            Timber.d("Device $deviceId status updated to $status via WebSocket")
        }.onFailure { e ->
            Timber.e(e, "DeviceRepo: failed to update device $deviceId status in DB")
        }
    }
}
