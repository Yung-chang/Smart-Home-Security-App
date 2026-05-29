package com.smarthome.guardian.presentation.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BackgroundStart = Color(0xFF0A0E1A)
private val BackgroundEnd   = Color(0xFF121827)
private val PrimaryBlue     = Color(0xFF00D4FF)
private val SecondaryPurple = Color(0xFF7B2FBE)
private val TextSecondary   = Color(0xFF8899AA)
private val ErrorRed        = Color(0xFFFF4444)

/**
 * 全螢幕生物辨識解鎖畫面。
 *
 * 畫面包含：
 * - Canvas 繪製的指紋動畫（脈衝光環）
 * - 狀態文字（等待中 / 識別中 / 失敗）
 * - PIN 備援連結
 *
 * @param onAuthenticated 驗證成功的導航回呼
 * @param onFallbackToPin 切換至 PIN 驗證
 */
@Composable
fun BiometricScreen(
    onAuthenticated: () -> Unit,
    onFallbackToPin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context   = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    // 成功 → 導航
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) onAuthenticated()
        if (authState is AuthState.RequiresPin)   onFallbackToPin()
    }

    // 畫面顯示時自動觸發生物辨識
    LaunchedEffect(Unit) {
        (context as? FragmentActivity)?.let { viewModel.loginWithBiometric(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundStart, BackgroundEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text       = "SmartHome Guardian",
                color      = PrimaryBlue,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )

            // ── 指紋動畫 ──────────────────────────────────────────────────────
            FingerprintAnimationView(
                isScanning = authState is AuthState.Loading,
                hasError   = authState is AuthState.Error,
                size       = 160.dp,
            )

            // ── 狀態文字 ──────────────────────────────────────────────────────
            val statusText = when (authState) {
                is AuthState.Loading -> "正在驗證中…"
                is AuthState.Error   -> (authState as AuthState.Error).message
                else                 -> "請觸碰指紋感應器"
            }
            val statusColor = if (authState is AuthState.Error) ErrorRed else TextSecondary

            Text(
                text      = statusText,
                color     = statusColor,
                fontSize  = 16.sp,
                textAlign = TextAlign.Center,
            )

            // ── 重試按鈕（錯誤時顯示）────────────────────────────────────────
            if (authState is AuthState.Error) {
                Button(
                    onClick = {
                        (context as? FragmentActivity)?.let { viewModel.loginWithBiometric(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Text("重試", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // ── PIN 備援連結 ───────────────────────────────────────────────────
            TextButton(onClick = onFallbackToPin) {
                Text(
                    text  = "使用 PIN 碼備援登入",
                    color = SecondaryPurple,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * 指紋掃描動畫元件。
 *
 * 使用 Canvas 繪製：
 * - 靜態指紋線條（簡化的同心弧線）
 * - 脈衝光環（掃描中持續擴散）
 * - 錯誤狀態時呈現紅色
 *
 * @param isScanning 是否正在掃描（驅動脈衝動畫）
 * @param hasError   是否顯示錯誤狀態
 * @param size       元件大小
 */
@Composable
private fun FingerprintAnimationView(
    isScanning: Boolean,
    hasError: Boolean,
    size: Dp,
) {
    val primaryColor = if (hasError) ErrorRed else PrimaryBlue

    // 脈衝動畫
    val infiniteTransition = rememberInfiniteTransition(label = "fp_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue  = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue  = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_radius",
    )

    Canvas(
        modifier = Modifier.size(size),
    ) {
        val cx     = center.x
        val cy     = center.y
        val radius = size.toPx() / 2f

        // 脈衝光環（掃描中才顯示）
        if (isScanning) {
            drawCircle(
                color  = primaryColor.copy(alpha = pulseAlpha * 0.4f),
                radius = radius * pulseRadius,
                style  = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color  = primaryColor.copy(alpha = pulseAlpha * 0.15f),
                radius = radius * (pulseRadius + 0.1f),
            )
        }

        // 外圓邊框
        drawCircle(
            color  = primaryColor.copy(alpha = 0.3f),
            radius = radius * 0.9f,
            style  = Stroke(width = 1.5.dp.toPx()),
        )

        // 指紋線條（5 條同心弧）
        val strokeWidth = 2.5.dp.toPx()
        val arcColor    = primaryColor.copy(alpha = if (hasError) 0.9f else 0.85f)

        drawFingerprintLines(
            cx          = cx,
            cy          = cy,
            baseRadius  = radius * 0.25f,
            maxRadius   = radius * 0.72f,
            color       = arcColor,
            strokeWidth = strokeWidth,
        )
    }
}

/** 繪製簡化的指紋弧線（5 條由內向外的同心弧）。 */
private fun DrawScope.drawFingerprintLines(
    cx: Float,
    cy: Float,
    baseRadius: Float,
    maxRadius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val step = (maxRadius - baseRadius) / 5f

    for (i in 0..4) {
        val r        = baseRadius + step * i
        val startDeg = -160f + i * 5f
        val sweepDeg = 320f - i * 10f

        val path = Path().apply {
            var first = true
            val steps = 60
            for (s in 0..steps) {
                val angle = Math.toRadians((startDeg + sweepDeg * s / steps).toDouble())
                val x = cx + r * Math.cos(angle).toFloat()
                val y = cy + r * Math.sin(angle).toFloat()
                if (first) { moveTo(x, y); first = false } else lineTo(x, y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun FingerprintIdlePreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundStart),
            contentAlignment = Alignment.Center,
        ) {
            FingerprintAnimationView(isScanning = false, hasError = false, size = 160.dp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun FingerprintScanningPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundStart),
            contentAlignment = Alignment.Center,
        ) {
            FingerprintAnimationView(isScanning = true, hasError = false, size = 160.dp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun FingerprintErrorPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundStart),
            contentAlignment = Alignment.Center,
        ) {
            FingerprintAnimationView(isScanning = false, hasError = true, size = 160.dp)
        }
    }
}
