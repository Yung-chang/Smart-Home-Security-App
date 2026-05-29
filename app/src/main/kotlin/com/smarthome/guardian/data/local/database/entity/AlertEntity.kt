package com.smarthome.guardian.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity

/** [SecurityAlert] 的 Room 持久化實體（PROMPT 09 接入 WebSocket 後使用）。 */
@Entity(tableName = "security_alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val type: String,
    val severity: String,
    val deviceId: String?,
    val message: String,
    val timestamp: Long,
    val isAcknowledged: Boolean = false,
    val actionTaken: String?,
) {
    fun toDomain(): SecurityAlert = SecurityAlert(
        id             = id,
        type           = AlertType.valueOf(type),
        severity       = Severity.valueOf(severity),
        deviceId       = deviceId,
        message        = message,
        timestamp      = timestamp,
        isAcknowledged = isAcknowledged,
        actionTaken    = actionTaken,
    )

    companion object {
        fun fromDomain(alert: SecurityAlert) = AlertEntity(
            id             = alert.id,
            type           = alert.type.name,
            severity       = alert.severity.name,
            deviceId       = alert.deviceId,
            message        = alert.message,
            timestamp      = alert.timestamp,
            isAcknowledged = alert.isAcknowledged,
            actionTaken    = alert.actionTaken,
        )
    }
}
