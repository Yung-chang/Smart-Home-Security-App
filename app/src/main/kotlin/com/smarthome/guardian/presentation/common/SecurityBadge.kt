package com.smarthome.guardian.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.ui.theme.SeverityCritical
import com.smarthome.guardian.ui.theme.SeverityHigh
import com.smarthome.guardian.ui.theme.SeverityLow
import com.smarthome.guardian.ui.theme.SeverityMedium
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme

/**
 * 嚴重程度徽章，視覺化顯示安全警報等級。
 *
 * | 等級     | 顏色   | 圖示    |
 * |----------|--------|---------|
 * | CRITICAL | 紅色   | Error   |
 * | HIGH     | 橙色   | Warning |
 * | MEDIUM   | 琥珀色 | Warning |
 * | LOW      | 綠色   | Info    |
 *
 * @param severity   嚴重程度
 * @param showIcon   是否顯示等級圖示（預設顯示）
 * @param compact    是否使用緊湊模式（僅顯示顏色圓點，不顯示文字）
 * @param modifier   Modifier
 */
@Composable
fun SecurityBadge(
    severity: Severity,
    showIcon: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (color, icon, label) = severity.badgeStyle()
    val bgColor = color.copy(alpha = 0.12f)
    val shape   = RoundedCornerShape(50)

    if (compact) {
        // 僅顯示顏色圓點（用於列表的緊湊版本）
        Spacer(
            modifier = modifier
                .size(8.dp)
                .clip(shape)
                .background(color),
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (showIcon) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = color,
                modifier           = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text       = label,
            color      = color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── 私有：嚴重程度樣式映射 ────────────────────────────────────────────────────

private data class BadgeStyle(val color: Color, val icon: ImageVector, val label: String)

private fun Severity.badgeStyle(): BadgeStyle = when (this) {
    Severity.CRITICAL -> BadgeStyle(SeverityCritical, Icons.Filled.Error,   "緊急")
    Severity.HIGH     -> BadgeStyle(SeverityHigh,     Icons.Filled.Warning, "高")
    Severity.MEDIUM   -> BadgeStyle(SeverityMedium,   Icons.Filled.Warning, "中")
    Severity.LOW      -> BadgeStyle(SeverityLow,      Icons.Filled.Info,    "低")
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun SecurityBadgePreview() {
    SmartHomeGuardianTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Severity.entries.forEach { severity ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    SecurityBadge(severity = severity)
                    SecurityBadge(severity = severity, showIcon = false)
                    SecurityBadge(severity = severity, compact = true)
                }
            }
        }
    }
}
