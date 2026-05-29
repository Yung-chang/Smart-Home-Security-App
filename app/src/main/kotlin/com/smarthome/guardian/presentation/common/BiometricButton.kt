package com.smarthome.guardian.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
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
import com.smarthome.guardian.ui.theme.Error
import com.smarthome.guardian.ui.theme.Primary
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import com.smarthome.guardian.ui.theme.Success
import com.smarthome.guardian.ui.theme.TextSecondary

/**
 * 生物辨識按鈕，依狀態顯示不同動畫效果。
 *
 * | 狀態     | 外觀                               |
 * |----------|------------------------------------|
 * | IDLE     | 靜態指紋圖示，帶外框               |
 * | SCANNING | 脈衝動畫 + 旋轉外環（掃描中）      |
 * | SUCCESS  | 綠色打勾圖示                       |
 * | ERROR    | 紅色叉叉圖示 + 震動動畫             |
 *
 * @param state    當前生物辨識狀態
 * @param size     按鈕直徑（預設 80dp）
 * @param onClick  點擊回呼（IDLE 狀態下有效）
 * @param modifier Modifier
 */
@Composable
fun BiometricButton(
    state: BiometricState,
    size: Dp = 80.dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val iconSize = size * 0.55f

    val bgColor by animateColorAsState(
        targetValue = when (state) {
            BiometricState.IDLE     -> Primary.copy(alpha = 0.08f)
            BiometricState.SCANNING -> Primary.copy(alpha = 0.15f)
            BiometricState.SUCCESS  -> Success.copy(alpha = 0.15f)
            BiometricState.ERROR    -> Error.copy(alpha = 0.15f)
        },
        label = "bioBgColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            BiometricState.IDLE     -> Primary.copy(alpha = 0.4f)
            BiometricState.SCANNING -> Primary
            BiometricState.SUCCESS  -> Success
            BiometricState.ERROR    -> Error
        },
        label = "bioBorderColor",
    )
    val iconColor by animateColorAsState(
        targetValue = when (state) {
            BiometricState.IDLE     -> Primary
            BiometricState.SCANNING -> Primary
            BiometricState.SUCCESS  -> Success
            BiometricState.ERROR    -> Error
        },
        label = "bioIconColor",
    )

    // SCANNING 狀態下的脈衝縮放動畫
    val scanScale by if (state == BiometricState.SCANNING) {
        rememberInfiniteTransition(label = "bioScan").animateFloat(
            initialValue  = 1f,
            targetValue   = 1.08f,
            animationSpec = infiniteRepeatable(
                animation  = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bioScanScale",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier         = modifier
            .size(size)
            .scale(scanScale)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (state == BiometricState.SCANNING) 2.dp else 1.5.dp,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(enabled = state == BiometricState.IDLE, onClick = onClick),
    ) {
        when (state) {
            BiometricState.IDLE,
            BiometricState.SCANNING -> Icon(
                imageVector        = Icons.Filled.Fingerprint,
                contentDescription = "生物辨識",
                tint               = iconColor,
                modifier           = Modifier.size(iconSize),
            )
            BiometricState.SUCCESS  -> Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = "辨識成功",
                tint               = iconColor,
                modifier           = Modifier.size(iconSize),
            )
            BiometricState.ERROR    -> Icon(
                imageVector        = Icons.Filled.Error,
                contentDescription = "辨識失敗",
                tint               = iconColor,
                modifier           = Modifier.size(iconSize),
            )
        }
    }
}

/** 生物辨識按鈕狀態。 */
enum class BiometricState {
    IDLE,      // 等待使用者點擊
    SCANNING,  // 掃描中（動畫顯示）
    SUCCESS,   // 辨識成功
    ERROR,     // 辨識失敗
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun BiometricButtonPreview() {
    SmartHomeGuardianTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            BiometricState.entries.forEach { state ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BiometricButton(state = state)
                    Text(
                        text  = state.name,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
