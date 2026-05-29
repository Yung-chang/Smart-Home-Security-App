package com.smarthome.guardian.domain.model

import androidx.compose.ui.graphics.Color

/**
 * 整體家庭安全等級，由後端 Alert Engine 計算後推送。
 * UI 層根據此值決定 [SecurityStatusBanner] 的顏色與文字。
 */
enum class SecurityLevel {
    /** 所有系統正常，無未處理警報。 */
    SECURE,

    /** 存在未確認的中低等級警報。 */
    WARNING,

    /** 偵測到高風險入侵事件或多個嚴重警報。 */
    ALERT;

    val displayColor: Color get() = when (this) {
        SECURE  -> Color(0xFF00C853)
        WARNING -> Color(0xFFFFB300)
        ALERT   -> Color(0xFFFF4444)
    }

    val displayText: String get() = when (this) {
        SECURE  -> "所有系統正常"
        WARNING -> "警報待處理"
        ALERT   -> "系統遭入侵偵測！"
    }

    val statusIcon: String get() = when (this) {
        SECURE  -> "✓"
        WARNING -> "⚠"
        ALERT   -> "🚨"
    }
}
