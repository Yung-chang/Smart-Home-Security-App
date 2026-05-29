package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart

import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.smarthome.guardian.domain.model.SensorReading
import com.smarthome.guardian.domain.model.SensorSnapshot
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SurfaceCard   = Color(0xFF1A2235)
private val WarningColor  = Color(0xFFFFB300)
private val ErrorRed      = Color(0xFFFF4444)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 感應器詳情面板。
 *
 * 功能：
 * - 當前感應值快照卡片（溫濕度/門窗狀態/煙霧/CO）
 * - Vico 折線圖（最近 24 小時歷史數據）
 * - 警報閾值設定 Slider
 */
@Composable
fun SensorPanel(
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
        // ── 當前感應值快照 ─────────────────────────────────────────────────────
        if (uiState.sensorSnapshots.isNotEmpty()) {
            Text("當前狀態", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.sensorSnapshots.forEach { snapshot ->
                    SensorValueCard(snapshot = snapshot, modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Vico 折線圖 ────────────────────────────────────────────────────────
        if (uiState.sensorHistory.isNotEmpty()) {
            Text("歷史數據（最近 24 小時）", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            SensorHistoryChart(history = uiState.sensorHistory)
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp).background(SurfaceCard, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("尚無歷史數據", color = TextSecondary)
            }
        }

        // ── 警報閾值 Slider ────────────────────────────────────────────────────
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("警報閾值", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("超過此值將觸發警報", color = TextSecondary, fontSize = 11.sp)
                }
                Text(
                    text       = uiState.threshold.let { if (it == 0f) "未設定" else "%.1f".format(it) },
                    color      = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value         = uiState.threshold,
                onValueChange = viewModel::setThreshold,
                valueRange    = 0f..100f,
                steps         = 19,
                colors        = SliderDefaults.colors(
                    thumbColor         = WarningColor,
                    activeTrackColor   = WarningColor,
                    inactiveTrackColor = SurfaceCard,
                ),
            )
        }
    }
}

// ── 感應器值卡片 ─────────────────────────────────────────────────────────────

@Composable
private fun SensorValueCard(
    snapshot: SensorSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color    = if (snapshot.isAlert) ErrorRed.copy(alpha = 0.15f) else SurfaceCard,
        shape    = RoundedCornerShape(12.dp),
        border   = if (snapshot.isAlert)
            androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)) else null,
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(snapshot.label, color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text       = "%.1f".format(snapshot.value),
                color      = if (snapshot.isAlert) ErrorRed else Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = snapshot.unit,
                color = if (snapshot.isAlert) ErrorRed.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

// ── Vico 折線圖 ──────────────────────────────────────────────────────────────

@Composable
private fun SensorHistoryChart(history: List<SensorReading>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(history) {
        withContext(Dispatchers.Default) {
            modelProducer.runTransaction {
                lineSeries { series(history.map { it.value }) }
            }
        }
    }

    Surface(
        color    = SurfaceCard,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(200.dp),
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
            ),
            modelProducer = modelProducer,
            modifier      = Modifier.padding(12.dp),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun SensorPanelPreview() {
    MaterialTheme {
        SensorPanel(
            uiState = DeviceUiState(
                sensorSnapshots = listOf(
                    SensorSnapshot("溫度", 26.5f, "℃"),
                    SensorSnapshot("濕度", 68f, "%", isAlert = true),
                    SensorSnapshot("CO", 12f, "ppm"),
                ),
                threshold = 70f,
                isLoading = false,
            ),
            viewModel = TODO("Preview only"),
        )
    }
}
