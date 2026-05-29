package com.smarthome.guardian.domain.repository

import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceOperation
import kotlinx.coroutines.flow.Flow

/**
 * 設備管理的 Repository 介面。
 * 實作類別負責合併本地快取（Room DB）與遠端資料（REST + WebSocket）。
 */
interface DeviceRepository {

    /** 訂閱所有設備清單（本地快取即時回傳，後台同步更新）。 */
    fun getDevices(): Flow<List<Device>>

    /** 訂閱單一設備（WebSocket 狀態變更時自動 emit）。 */
    fun getDevice(id: String): Flow<Device?>

    /** 依房間 ID 篩選設備清單。 */
    fun getDevicesByRoom(roomId: String): Flow<List<Device>>

    /**
     * 發送設備控制指令。
     *
     * 每筆指令會以 HMAC-SHA256 簽章後送出，防止中途竄改。
     *
     * @param deviceId  目標設備 ID
     * @param operation 執行的操作
     * @param payload   操作參數（如亮度值、目標溫度等），JSON 字串
     */
    suspend fun sendCommand(
        deviceId: String,
        operation: DeviceOperation,
        payload: String = "{}",
    ): Result<Unit>

    /** 更新設備設定（名稱、所在房間等）。 */
    suspend fun updateSettings(device: Device): Result<Unit>

    /** 強制從伺服器重新拉取所有設備清單。 */
    suspend fun refresh(): Result<Unit>

    /** 新增設備至本地資料庫（無後端時的本地暫存）。 */
    suspend fun addDevice(device: Device): Result<Unit>
}
