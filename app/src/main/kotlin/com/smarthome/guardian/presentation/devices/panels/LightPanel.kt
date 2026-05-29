package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel
import kotlin.math.*

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SurfaceCard   = Color(0xFF1A2235)
private val TextSecondary = Color(0xFF8899AA)

/** 預設場景定義。 */
private val SCENES = listOf(
    Triple("閱讀", 5000f, 80f),   // 色溫 5000K, 亮度 80%
    Triple("電影", 2700f, 20f),   // 暖色, 低亮
    Triple("睡眠", 2200f, 5f),    // 極暖, 極低
    Triple("工作", 6000f, 100f),  // 冷白, 全亮
)

/**
 * 智慧燈光控制面板。
 *
 * 功能：
 * - 亮度 Slider（0–100%）
 * - 色溫 Slider（2700–6500K）
 * - Canvas 圓形色彩選取器
 * - 場景預設按鈕（閱讀/電影/睡眠/工作）
 * - 定時開關設定（骨架，PROMPT 07 擴充）
 */
@Composable
fun LightPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 亮度 Slider ────────────────────────────────────────────────────────
        SliderSection(
            label     = "亮度",
            value     = uiState.brightness,
            range     = 0f..100f,
            unit      = "%",
            onChanged = viewModel::setBrightness,
            trackGradient = Brush.horizontalGradient(listOf(Color.Black, Color.White)),
        )

        // ── 色溫 Slider ────────────────────────────────────────────────────────
        SliderSection(
            label     = "色溫",
            value     = uiState.colorTemp,
            range     = 2700f..6500f,
            unit      = "K",
            onChanged = viewModel::setColorTemp,
            trackGradient = Brush.horizontalGradient(
                listOf(Color(0xFFFF9500), Color(0xFFFFFFFF), Color(0xFFB0D4FF))
            ),
        )

        // ── 圓形色彩選取器 ─────────────────────────────────────────────────────
        Text("顏色", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        CircularColorPicker(
            selectedColor = Color(uiState.lightColor),
            onColorSelected = { viewModel.setColor(it.toArgb()) },
            modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
        )

        // ── 場景預設 ───────────────────────────────────────────────────────────
        Text("場景預設", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SCENES.forEach { (name, temp, brightness) ->
                OutlinedButton(
                    onClick  = { viewModel.applyScene(name) },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(name, fontSize = 12.sp)
                }
            }
        }

        // ── 定時開關 ───────────────────────────────────────────────────────────
        Surface(
            color  = SurfaceCard,
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier            = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Column {
                    Text("定時開關", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("待 PROMPT 07 存取控制完成後啟用", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = false,
                    onCheckedChange = { /* TODO PROMPT 07 */ },
                    enabled = false,
                )
            }
        }
    }
}

// ── 滑桿區塊 ──────────────────────────────────────────────────────────────────

@Composable
private fun SliderSection(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChanged: (Float) -> Unit,
    trackGradient: Brush,
) {
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "${value.toInt()} $unit",
                color      = PrimaryBlue,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value         = value,
            onValueChange = onChanged,
            valueRange    = range,
            colors        = SliderDefaults.colors(
                thumbColor           = PrimaryBlue,
                activeTrackColor     = PrimaryBlue,
                inactiveTrackColor   = SurfaceCard,
            ),
        )
    }
}

// ── 圓形色彩選取器（Canvas 實作）─────────────────────────────────────────────

@Composable
private fun CircularColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val dx = offset.x - center.x
                val dy = offset.y - center.y
                val dist = sqrt(dx * dx + dy * dy)
                val radius = minOf(size.width, size.height) / 2f
                if (dist <= radius) {
                    val hue       = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toDouble()) + 360) % 360
                    val sat       = (dist / radius).coerceIn(0f, 1f)
                    val hsv       = floatArrayOf(hue.toFloat(), sat, 1f)
                    onColorSelected(Color(android.graphics.Color.HSVToColor(hsv)))
                }
            }
        },
    ) {
        center = this.center
        val radius = size.minDimension / 2f

        // 色相環（sweep gradient）
        drawCircle(
            brush  = Brush.sweepGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                )
            ),
            radius = radius,
        )
        // 白色中心漸層（飽和度）
        drawCircle(
            brush  = Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius),
            radius = radius,
        )
        // 外框
        drawCircle(
            color  = Color.White.copy(alpha = 0.2f),
            radius = radius,
            style  = Stroke(width = 2.dp.toPx()),
        )
        // 目前選取位置指示點
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        val hRad   = Math.toRadians(hsv[0].toDouble())
        val selDist = hsv[1] * radius
        val selX   = center.x + (selDist * cos(hRad)).toFloat()
        val selY   = center.y + (selDist * sin(hRad)).toFloat()
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(selX, selY))
        drawCircle(color = selectedColor, radius = 6.dp.toPx(), center = Offset(selX, selY))
    }
}
