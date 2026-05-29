package com.smarthome.guardian.domain.model

import androidx.compose.ui.graphics.Color

/**
 * 安全警報的領域模型。
 *
 * @property id              唯一識別碼
 * @property type            警報類型
 * @property severity        嚴重程度
 * @property deviceId        觸發設備 ID（null 表示系統級事件）
 * @property message         警報描述
 * @property timestamp       觸發時間 Unix epoch 毫秒
 * @property isAcknowledged  是否已確認
 * @property actionTaken     系統自動處理記錄
 */
data class SecurityAlert(
    val id: String,
    val type: AlertType,
    val severity: Severity,
    val deviceId: String? = null,
    val message: String,
    val timestamp: Long,
    val isAcknowledged: Boolean = false,
    val actionTaken: String? = null,
)

/** 警報類型。 */
enum class AlertType(val displayName: String) {
    INTRUSION("入侵偵測"),
    DEVICE_OFFLINE("設備離線"),
    AUTH_FAIL("驗證失敗"),
    ANOMALY("異常行為"),
    DEVICE_TAMPER("設備遭竄改"),
    SYSTEM("系統事件"),
}

/** 嚴重程度，包含顯示所需的顏色與中文標籤。 */
enum class Severity(val displayName: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    CRITICAL("緊急");

    val color: Color get() = when (this) {
        LOW      -> Color(0xFF4CAF50)
        MEDIUM   -> Color(0xFFFFB300)
        HIGH     -> Color(0xFFFF6D00)
        CRITICAL -> Color(0xFFFF4444)
    }
}
