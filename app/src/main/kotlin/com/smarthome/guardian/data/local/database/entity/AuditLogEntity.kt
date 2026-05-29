package com.smarthome.guardian.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.AuditLog

/**
 * [AuditLog] 的 Room 持久化實體。
 *
 * 以字串儲存 [action]（[AuditAction.name]），避免 Room 無法直接對應 enum 的問題。
 * 讀取時透過 [toDomain] 轉回 domain 物件。
 *
 * 索引涵蓋最常用的查詢維度（時間、用戶、動作類型、目標），
 * 確保大量日誌下查詢效能。
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index("timestamp"),
        Index("userId"),
        Index("action"),
        Index("targetId"),
    ],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val action: String,
    val targetId: String?,
    val before: String?,
    val after: String?,
    val ipAddress: String,
    val deviceFingerprint: String,
    val timestamp: Long,
    val signature: String,
) {
    fun toDomain(): AuditLog = AuditLog(
        id                = id,
        userId            = userId,
        action            = AuditAction.valueOf(action),
        targetId          = targetId,
        before            = before,
        after             = after,
        ipAddress         = ipAddress,
        deviceFingerprint = deviceFingerprint,
        timestamp         = timestamp,
        signature         = signature,
    )

    companion object {
        fun fromDomain(log: AuditLog) = AuditLogEntity(
            id                = log.id,
            userId            = log.userId,
            action            = log.action.name,
            targetId          = log.targetId,
            before            = log.before,
            after             = log.after,
            ipAddress         = log.ipAddress,
            deviceFingerprint = log.deviceFingerprint,
            timestamp         = log.timestamp,
            signature         = log.signature,
        )
    }
}
