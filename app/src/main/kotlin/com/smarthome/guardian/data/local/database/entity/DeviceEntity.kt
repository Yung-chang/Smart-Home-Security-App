package com.smarthome.guardian.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.DeviceType

/** [Device] 的 Room 持久化實體（PROMPT 09 接入 REST API 後使用）。 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val status: String,
    val isLocked: Boolean = false,
    val lastSeen: Long = 0L,
    val firmware: String = "1.0.0",
    val macAddress: String = "",
    val isOn: Boolean = false,
    val batteryLevel: Int? = null,
    val signalStrength: Int? = null,
) {
    fun toDomain(): Device = Device(
        id            = id,
        name          = name,
        type          = DeviceType.valueOf(type),
        roomId        = roomId,
        status        = DeviceStatus.valueOf(status),
        isLocked      = isLocked,
        lastSeen      = lastSeen,
        firmware      = firmware,
        macAddress    = macAddress,
        isOn          = isOn,
        batteryLevel  = batteryLevel,
        signalStrength= signalStrength,
    )

    companion object {
        fun fromDomain(device: Device) = DeviceEntity(
            id            = device.id,
            name          = device.name,
            type          = device.type.name,
            roomId        = device.roomId,
            status        = device.status.name,
            isLocked      = device.isLocked,
            lastSeen      = device.lastSeen,
            firmware      = device.firmware,
            macAddress    = device.macAddress,
            isOn          = device.isOn,
            batteryLevel  = device.batteryLevel,
            signalStrength= device.signalStrength,
        )
    }
}
