package com.smarthome.guardian.data.local.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.smarthome.guardian.data.local.database.entity.AuditLogEntity

/**
 * 稽核日誌的 Room DAO。
 *
 * 使用 [PagingSource] 支援 Paging 3 分頁載入，
 * 確保大量日誌（10 萬筆以上）仍能流暢渲染。
 */
@Dao
interface AuditDao {

    /**
     * 批次插入日誌（PROMPT 08 AuditLogger 使用）。
     * [OnConflictStrategy.IGNORE] 確保重複 ID 不會拋出例外。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insertAll(logs: List<AuditLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE id = :logId LIMIT 1")
    suspend fun getById(logId: String): AuditLogEntity?

    /**
     * 取得符合篩選條件的分頁日誌（降冪時間排序）。
     *
     * 所有條件均為可選：傳入 null 表示不限制該欄位。
     * [query] 會模糊比對 userId、action、targetId。
     */
    @Query("""
        SELECT * FROM audit_logs
        WHERE (:userId   IS NULL OR userId   = :userId)
          AND (:action   IS NULL OR action   = :action)
          AND (:targetId IS NULL OR targetId = :targetId)
          AND (:query    IS NULL OR userId   LIKE '%' || :query || '%'
                                OR action   LIKE '%' || :query || '%'
                                OR targetId LIKE '%' || :query || '%')
          AND (:startMs  IS NULL OR timestamp >= :startMs)
          AND (:endMs    IS NULL OR timestamp <= :endMs)
        ORDER BY timestamp DESC
    """)
    fun getPagedFiltered(
        userId: String?,
        action: String?,
        targetId: String?,
        query: String?,
        startMs: Long?,
        endMs: Long?,
    ): PagingSource<Int, AuditLogEntity>

    /** 取得所有符合時間範圍的日誌（匯出用，不分頁）。 */
    @Query("""
        SELECT * FROM audit_logs
        WHERE (:startMs IS NULL OR timestamp >= :startMs)
          AND (:endMs   IS NULL OR timestamp <= :endMs)
        ORDER BY timestamp DESC
    """)
    suspend fun getAllForExport(startMs: Long?, endMs: Long?): List<AuditLogEntity>

    /** 刪除指定時間點之前的舊日誌（定期清理，防止 DB 無限膨脹）。 */
    @Query("DELETE FROM audit_logs WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
