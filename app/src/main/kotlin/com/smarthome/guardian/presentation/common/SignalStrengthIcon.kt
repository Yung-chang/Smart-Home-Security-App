package com.smarthome.guardian.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.ui.theme.Primary
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import com.smarthome.guardian.ui.theme.TextSecondary

/**
 * 設備訊號強度圖示（4 格條狀，依強度填色）。
 *
 * ## 強度區間
 * | 格數（亮起） | 強度範圍 | 顯示意義  |
 * |------------|----------|----------|
 * | 0          | null / 0  | 無訊號   |
 * | 1          | 1–25      | 微弱     |
 * | 2          | 26–50     | 普通     |
 * | 3          | 51–75     | 良好     |
 * | 4          | 76–100    | 優秀     |
 *
 * @param signalPercent 訊號強度百分比（0–100），null 表示不支援
 * @param size          圖示尺寸（預設 16dp）
 * @param activeColor   已填充格子的顏色（預設主題色）
 * @param inactiveColor 未填充格子的顏色
 * @param modifier      Modifier
 */
@Composable
fun SignalStrengthIcon(
    signalPercent: Int?,
    size: Dp = 16.dp,
    activeColor: Color = Primary,
    inactiveColor: Color = TextSecondary.copy(alpha = 0.25f),
    modifier: Modifier = Modifier,
) {
    val activeBars = when {
        signalPercent == null || signalPercent <= 0 -> 0
        signalPercent <= 25  -> 1
        signalPercent <= 50  -> 2
        signalPercent <= 75  -> 3
        else                 -> 4
    }

    Canvas(modifier = modifier.size(size)) {
        val totalBars  = 4
        val barWidth   = size.toPx() * 0.18f
        val gap        = size.toPx() * 0.09f
        val maxHeight  = size.toPx()
        val cornerR    = CornerRadius(2.dp.toPx())

        for (i in 0 until totalBars) {
            val barHeight   = maxHeight * (i + 1) / totalBars
            val barLeft     = i * (barWidth + gap)
            val barTop      = maxHeight - barHeight
            val isActive    = i < activeBars

            drawRoundRect(
                color        = if (isActive) activeColor else inactiveColor,
                topLeft      = Offset(barLeft, barTop),
                size         = Size(barWidth, barHeight),
                cornerRadius = cornerR,
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun SignalStrengthIconPreview() {
    SmartHomeGuardianTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.padding(20.dp),
        ) {
            listOf(null, 10, 40, 65, 90).forEach { signal ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SignalStrengthIcon(signalPercent = signal, size = 20.dp)
                    Text(
                        text     = signal?.let { "$it%" } ?: "N/A",
                        color    = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
