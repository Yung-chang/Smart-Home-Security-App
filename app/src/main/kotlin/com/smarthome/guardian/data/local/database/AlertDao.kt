package com.smarthome.guardian.data.local.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.smarthome.guardian.data.local.database.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

/** 安全警報的 Room DAO（PROMPT 09 WebSocket 本地快取用）。 */
@Dao
interface AlertDao {

    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM security_alerts WHERE isAcknowledged = 0")
    fun getUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(alerts: List<AlertEntity>)

    @Upsert
    suspend fun upsert(alert: AlertEntity)

    @Query("UPDATE security_alerts SET isAcknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("UPDATE security_alerts SET isAcknowledged = 1 WHERE id IN (:ids)")
    suspend fun acknowledgeAll(ids: List<String>)

    @Query("DELETE FROM security_alerts WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
