package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel

private val AlertRed      = Color(0xFFFF4444)
private val SurfaceCard   = Color(0xFF1A2235)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)
private val SuccessGreen  = Color(0xFF00C853)

private val DELAY_OPTIONS = listOf("立即", "30 秒", "1 分鐘", "3 分鐘")

/**
 * 警報器控制面板。
 * - 觸發狀態指示
 * - 靜音按鈕
 * - 音量調整
 * - 觸發延遲設定（防止誤觸）
 * - 測試警報
 */
@Composable
fun AlarmPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
) {
    val triggered   = uiState.alarmTriggered
    val volume      = uiState.alarmVolume
    val delay       = uiState.alarmDelay
    val isOn        = uiState.device?.isOn ?: false
    val statusColor = if (triggered) AlertRed else if (isOn) SuccessGreen else TextSecondary

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(20.dp),
    ) {
        // ── 狀態指示圓 ────────────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(90.dp))
                .background(statusColor.copy(alpha = 0.1f))
                .border(3.dp, statusColor, RoundedCornerShape(90.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.NotificationImportant,
                    null,
                    tint     = statusColor,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = when {
                        triggered -> "警報觸發中！"
                        isOn      -> "監控中"
                        else      -> "已停用"
                    },
                    color      = statusColor,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ── 靜音 / 測試 ──────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick  = { viewModel.silenceAlarm() },
                enabled  = triggered,
                modifier = Modifier.weight(1f).height(52.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                border   = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
            ) {
                Text("靜音", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick  = { viewModel.testAlarm() },
                enabled  = isOn && !triggered,
                modifier = Modifier.weight(1f).height(52.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
            ) {
                Text("測試警報", fontWeight = FontWeight.Bold)
            }
        }

        // ── 音量 ──────────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.VolumeUp, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Text("警報音量", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text("${volume.toInt()}%", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value         = volume,
                onValueChange = { viewModel.setAlarmVolume(it) },
                valueRange    = 0f..100f,
                modifier      = Modifier.fillMaxWidth(),
                colors        = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue),
            )
        }

        // ── 觸發延遲 ──────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("觸發延遲", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("感應到異常後延遲多久才觸發警報（防誤觸）", color = TextSecondary, fontSize = 11.sp)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DELAY_OPTIONS.forEach { d ->
                    val selected = d == delay
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setAlarmDelay(d) },
                        color    = if (selected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceCard,
                        shape    = RoundedCornerShape(8.dp),
                        border   = if (selected) ButtonDefaults.outlinedButtonBorder else null,
                    ) {
                        Text(
                            text     = d,
                            color    = if (selected) PrimaryBlue else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }

        // ── 開關 ──────────────────────────────────────────────────────────────
        Button(
            onClick  = { viewModel.toggleDevice(!isOn) },
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isOn) AlertRed else SuccessGreen),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
        ) {
            Text(
                text       = if (isOn) "停用警報器" else "啟用警報器",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
            )
        }
    }
}
