package com.smarthome.guardian.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.Severity

/**
 * 嚴重程度標籤元件（用於警報列表、設備卡片等）。
 *
 * @param severity 嚴重程度（決定顏色）
 * @param modifier  外部修飾符
 */
@Composable
fun SeverityBadge(
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = severity.color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = severity.displayName,
            color      = severity.color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * AppBar 通知鈴鐺 Badge（顯示未讀警報數）。
 *
 * @param count 未讀數量（0 時不顯示）
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(
                color = Color(0xFFFF4444),
                shape = RoundedCornerShape(9.dp),
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = if (count > 99) "99+" else count.toString(),
            color      = Color.White,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun BadgesPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Severity.values().forEach { SeverityBadge(it) }
        }
    }
}
