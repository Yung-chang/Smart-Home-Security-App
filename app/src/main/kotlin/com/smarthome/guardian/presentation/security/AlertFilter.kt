package com.smarthome.guardian.presentation.security

import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.Severity

/**
 * 警報歷史篩選條件。
 * 所有欄位為 null 表示不限制（全選）。
 *
 * @property startDate   起始日期 epoch 毫秒
 * @property endDate     結束日期 epoch 毫秒
 * @property severities  嚴重程度白名單
 * @property types       警報類型白名單
 * @property deviceIds   設備 ID 白名單
 * @property onlyUnread  是否只顯示未確認
 */
data class AlertFilter(
    val startDate: Long?             = null,
    val endDate: Long?               = null,
    val severities: Set<Severity>    = emptySet(),
    val types: Set<AlertType>        = emptySet(),
    val deviceIds: Set<String>       = emptySet(),
    val onlyUnread: Boolean          = false,
) {
    val isActive: Boolean
        get() = startDate != null || endDate != null || severities.isNotEmpty() ||
                types.isNotEmpty() || deviceIds.isNotEmpty() || onlyUnread

    companion object {
        val NONE = AlertFilter()
    }
}
