package com.smarthome.guardian.domain.model

/**
 * 感應器單筆讀取值（用於 Vico 折線圖）。
 *
 * @property timestamp  量測時間 epoch 毫秒
 * @property value      量測數值
 * @property unit       單位字串（℃ / % / ppm 等）
 */
data class SensorReading(
    val timestamp: Long,
    val value: Float,
    val unit: String,
)

/**
 * 感應器目前狀態的快照（顯示於詳情頁頂部）。
 *
 * @property label    顯示標籤（如「溫度」「CO 濃度」）
 * @property value    當前值
 * @property unit     單位
 * @property isAlert  是否超過警報閾值
 */
data class SensorSnapshot(
    val label: String,
    val value: Float,
    val unit: String,
    val isAlert: Boolean = false,
)
