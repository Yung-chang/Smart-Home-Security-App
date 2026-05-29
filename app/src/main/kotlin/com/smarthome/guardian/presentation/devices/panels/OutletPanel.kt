package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SurfaceCard   = Color(0xFF1A2235)
private val TextSecondary = Color(0xFF8899AA)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningAmber  = Color(0xFFFFB300)

/**
 * 智慧插座控制面板。
 * 提供開/關切換、即時用電量顯示，以及定時開關設定。
 */
@Composable
fun OutletPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
) {
    val device = uiState.device ?: return
    val isOn   = device.isOn

    // 模擬即時用電數據（isOn 時才有數值）
    val currentWatts   = remember(isOn) { if (isOn) (50..280).random().toFloat() else 0f }
    val monthlyKwh     = remember { (12..87).random().toFloat() / 10f }
    val todayKwh       = remember { (5..45).random().toFloat() / 10f }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── 開關主卡 ──────────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape  = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text       = if (isOn) "已開啟" else "已關閉",
                        color      = if (isOn) SuccessGreen else TextSecondary,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text     = if (isOn) "插座供電中" else "插座停電",
                        color    = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked         = isOn,
                    onCheckedChange = { viewModel.toggleDevice(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = Color.White,
                        checkedTrackColor  = SuccessGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF334155),
                    ),
                )
            }
        }

        // ── 即時用電 ──────────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape  = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("用電資訊", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PowerStat(
                        label = "即時功率",
                        value = if (isOn) "%.0f W".format(currentWatts) else "0 W",
                        color = if (isOn && currentWatts > 200f) WarningAmber else PrimaryBlue,
                    )
                    PowerStat(
                        label = "今日用電",
                        value = if (isOn) "%.1f kWh".format(todayKwh) else "0 kWh",
                        color = PrimaryBlue,
                    )
                    PowerStat(
                        label = "本月用電",
                        value = "%.1f kWh".format(monthlyKwh),
                        color = PrimaryBlue,
                    )
                }

                if (isOn && currentWatts > 200f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Bolt, null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = "用電量偏高，請確認連接設備是否正常",
                            color    = WarningAmber,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // ── 定時開關 ──────────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape  = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("定時開關", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                OutletScheduleRow(uiState = uiState, viewModel = viewModel)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PowerStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun OutletScheduleRow(uiState: DeviceUiState, viewModel: DeviceViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var onTime     by remember { mutableStateOf(uiState.scheduleOnTime) }
    var offTime    by remember { mutableStateOf(uiState.scheduleOffTime) }
    var enabled    by remember { mutableStateOf(uiState.scheduleEnabled) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column {
            if (enabled)
                Text("開 $onTime　關 $offTime", color = PrimaryBlue, fontSize = 12.sp)
            else
                Text("未設定", color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked         = enabled,
            onCheckedChange = {
                enabled = it
                if (it) showDialog = true
                else viewModel.setSchedule(false, onTime, offTime)
            },
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("設定定時開關") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("開啟時間", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value         = onTime,
                        onValueChange = { if (it.length <= 5) onTime = it },
                        label         = { Text("HH:MM") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    Text("關閉時間", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value         = offTime,
                        onValueChange = { if (it.length <= 5) offTime = it },
                        label         = { Text("HH:MM") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSchedule(true, onTime, offTime)
                    showDialog = false
                }) { Text("確認") }
            },
            dismissButton = {
                TextButton(onClick = {
                    enabled = false
                    showDialog = false
                }) { Text("取消") }
            },
        )
    }
}
