package com.smarthome.guardian.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.smarthome.guardian.data.local.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/** 設備的 Room DAO（PROMPT 09 WebSocket 本地快取用）。 */
@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE roomId = :roomId ORDER BY name ASC")
    fun getByRoom(roomId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DeviceEntity?

    @Upsert
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun count(): Int

    @Query("SELECT * FROM devices")
    suspend fun getAllOnce(): List<DeviceEntity>
}
