package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SurfaceCard   = Color(0xFF1A2235)
private val TextSecondary = Color(0xFF8899AA)
private val WarmOrange    = Color(0xFFFF6D00)

private val AC_MODES = listOf("冷氣", "暖氣", "自動", "除濕", "送風")

/**
 * 溫控器（冷暖氣）控制面板。
 *
 * 功能：
 * - 目標溫度調整（16–30°C）
 * - 模式切換（冷氣／暖氣／自動／除濕／送風）
 * - 開關控制
 */
@Composable
fun ThermostatPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
) {
    val device  = uiState.device ?: return
    val isOn    = device.isOn
    val temp    = uiState.targetTemp
    val mode    = uiState.acMode
    val modeColor = if (mode == "暖氣") WarmOrange else PrimaryBlue

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(20.dp),
    ) {
        // ── 溫度顯示 ──────────────────────────────────────────────────────────
        Box(
            modifier            = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceCard)
                .border(3.dp, if (isOn) modeColor else TextSecondary, RoundedCornerShape(100.dp)),
            contentAlignment    = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = if (isOn) "${temp.toInt()}°C" else "OFF",
                    color      = if (isOn) modeColor else TextSecondary,
                    fontSize   = 52.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = if (isOn) mode else "已關閉",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }

        // ── 溫度調整 ──────────────────────────────────────────────────────────
        if (isOn) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 降溫
                FilledIconButton(
                    onClick = { viewModel.setTargetTemp(temp - 1f) },
                    enabled = temp > 16f,
                    colors  = IconButtonDefaults.filledIconButtonColors(containerColor = SurfaceCard),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "降溫", tint = PrimaryBlue)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("目標溫度", color = TextSecondary, fontSize = 12.sp)
                    Text("${temp.toInt()}°C", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                // 升溫
                FilledIconButton(
                    onClick = { viewModel.setTargetTemp(temp + 1f) },
                    enabled = temp < 30f,
                    colors  = IconButtonDefaults.filledIconButtonColors(containerColor = SurfaceCard),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "升溫", tint = PrimaryBlue)
                }
            }

            // ── 模式選擇 ──────────────────────────────────────────────────────
            Text("運作模式", color = TextSecondary, fontSize = 13.sp)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AC_MODES.forEach { m ->
                    val selected = m == mode
                    Surface(
                        modifier      = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setAcMode(m) },
                        color         = if (selected) modeColor.copy(alpha = 0.15f) else SurfaceCard,
                        shape         = RoundedCornerShape(8.dp),
                        border        = if (selected)
                            ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                            else null,
                    ) {
                        Text(
                            text     = m,
                            color    = if (selected) modeColor else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }

        // ── 開關 ──────────────────────────────────────────────────────────────
        Button(
            onClick = { viewModel.toggleDevice(!isOn) },
            colors  = ButtonDefaults.buttonColors(
                containerColor = if (isOn) modeColor else SurfaceCard,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
        ) {
            Text(
                text       = if (isOn) "關閉溫控器" else "開啟溫控器",
                color      = if (isOn) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
            )
        }
    }
}
