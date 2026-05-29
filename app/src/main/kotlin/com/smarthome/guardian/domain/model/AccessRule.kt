package com.smarthome.guardian.domain.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/**
 * 單條 RBAC 存取規則。
 *
 * @property id                規則唯一識別碼
 * @property userId            適用使用者 ID（`"*"` 表示所有使用者）
 * @property deviceId          適用設備 ID（`"*"` 表示所有設備）
 * @property allowedOperations 允許的操作集合
 * @property timeWindow        時間窗口限制（null = 不限時間）
 * @property isEnabled         規則是否啟用
 * @property expiresAt         到期時間 epoch 毫秒（null = 永不過期）
 */
data class AccessRule(
    val id: String,
    val userId: String,
    val deviceId: String,
    val allowedOperations: Set<DeviceOperation>,
    val timeWindow: TimeWindow? = null,
    val isEnabled: Boolean = true,
    val expiresAt: Long? = null,
)

/**
 * 時間窗口限制。
 *
 * @property daysOfWeek 允許操作的星期幾
 * @property startTime  允許開始時間（本地時間）
 * @property endTime    允許結束時間（本地時間）
 * @property timezone   時區
 */
data class TimeWindow(
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val timezone: ZoneId = ZoneId.systemDefault(),
)
