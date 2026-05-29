package com.smarthome.guardian.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.ui.theme.Error
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import com.smarthome.guardian.ui.theme.Success
import com.smarthome.guardian.ui.theme.TextSecondary

/**
 * 設備狀態指示燈（圓點 + 可選的脈衝動畫）。
 *
 * - **ONLINE**：綠色圓點，帶外環脈衝動畫（提示設備活躍）
 * - **OFFLINE**：灰色圓點，無動畫
 * - **ERROR**：紅色圓點，無動畫
 *
 * @param status    設備連線狀態
 * @param dotSize   主圓點直徑（預設 10dp）
 * @param showLabel 是否顯示狀態文字標籤
 * @param modifier  Modifier
 */
@Composable
fun StatusIndicator(
    status: DeviceStatus,
    dotSize: Dp = 10.dp,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (dotColor, labelText) = when (status) {
        DeviceStatus.ONLINE  -> Success to "在線"
        DeviceStatus.OFFLINE -> TextSecondary to "離線"
        DeviceStatus.ERROR   -> Error to "異常"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 脈衝外環（僅 ONLINE 狀態顯示）
            if (status == DeviceStatus.ONLINE) {
                PulseRing(color = dotColor, dotSize = dotSize)
            }

            // 主圓點
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }

        if (showLabel) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text       = labelText,
                color      = dotColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── 脈衝外環動畫 ──────────────────────────────────────────────────────────────

@Composable
private fun PulseRing(color: Color, dotSize: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 2.2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .size(dotSize)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun StatusIndicatorPreview() {
    SmartHomeGuardianTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            DeviceStatus.entries.forEach { status ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    StatusIndicator(status = status, dotSize = 10.dp)
                    StatusIndicator(status = status, dotSize = 12.dp, showLabel = true)
                }
            }
        }
    }
}
