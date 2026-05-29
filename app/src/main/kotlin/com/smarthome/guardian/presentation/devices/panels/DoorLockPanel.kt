package com.smarthome.guardian.presentation.devices.panels

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.presentation.common.ConfirmDialog
import com.smarthome.guardian.presentation.common.TimelineItem
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SuccessGreen  = Color(0xFF00C853)
private val ErrorRed      = Color(0xFFFF4444)
private val SurfaceCard   = Color(0xFF1A2235)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 智慧門鎖控制面板。
 *
 * 功能：
 * - 大型鎖頭動畫（旋轉切換鎖定/解鎖）
 * - 上鎖/解鎖按鈕（需確認對話框）
 * - 進出記錄時間軸
 * - 臨時密碼生成
 * - 電池電量顯示
 */
@Composable
fun DoorLockPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
    modifier: Modifier = Modifier,
) {
    val device    = uiState.device ?: return
    val isLocked  = !device.isOn   // isOn = true → 解鎖

    // 確認對話框
    if (uiState.showConfirmDialog) {
        ConfirmDialog(
            title       = uiState.confirmTitle,
            message     = uiState.confirmMessage,
            icon        = if (isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
            isDangerous = !isLocked, // 解鎖是高風險操作
            onConfirm   = viewModel::executeConfirmedCommand,
            onDismiss   = viewModel::cancelCommand,
        )
    }

    // 臨時密碼對話框
    uiState.tempCode?.let { code ->
        AlertDialog(
            onDismissRequest = viewModel::clearTempCode,
            containerColor   = SurfaceCard,
            shape            = RoundedCornerShape(16.dp),
            title            = { Text("臨時密碼", color = Color.White, fontWeight = FontWeight.Bold) },
            text             = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("此密碼 10 分鐘內有效，使用一次即失效", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text       = code,
                        color      = PrimaryBlue,
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::clearTempCode,
                    colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) { Text("關閉", color = Color.Black) }
            },
        )
    }

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 鎖頭動畫 ──────────────────────────────────────────────────────────
        item {
            LockAnimation(
                isLocked   = isLocked,
                isLoading  = uiState.isSendingCommand,
                modifier   = Modifier.fillMaxWidth(),
            )
        }

        // ── 上鎖 / 解鎖 按鈕 ─────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val cmd = DeviceCommand(deviceId = device.id, type = CommandType.UNLOCK)
                        viewModel.requestCommand(cmd, "解鎖門鎖", "確定要解鎖「${device.name}」嗎？請確認周遭環境安全。")
                    },
                    enabled  = isLocked && !uiState.isSendingCommand,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("解鎖", fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Button(
                    onClick = {
                        val cmd = DeviceCommand(deviceId = device.id, type = CommandType.LOCK)
                        viewModel.requestCommand(cmd, "上鎖門鎖", "確定要上鎖「${device.name}」嗎？")
                    },
                    enabled  = !isLocked && !uiState.isSendingCommand,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Lock, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("上鎖", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        }

        // ── 電池電量 + 臨時密碼 ───────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                device.batteryLevel?.let { battery ->
                    InfoChip(
                        icon  = Icons.Filled.BatteryFull,
                        label = "電量 $battery%",
                        color = when {
                            battery > 50 -> SuccessGreen
                            battery > 20 -> Color(0xFFFFB300)
                            else         -> ErrorRed
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick  = viewModel::generateTempCode,
                    enabled  = !uiState.isSendingCommand,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Filled.Password, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("臨時密碼", fontSize = 13.sp)
                }
            }
        }

        // ── 進出記錄標題 ──────────────────────────────────────────────────────
        item {
            Text("進出記錄", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // ── Timeline ─────────────────────────────────────────────────────────
        if (uiState.accessLogs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("尚無進出記錄", color = TextSecondary)
                }
            }
        } else {
            itemsIndexed(uiState.accessLogs) { index, log ->
                TimelineItem(log = log, isLast = index == uiState.accessLogs.lastIndex)
            }
        }
    }
}

// ── 鎖頭動畫 ──────────────────────────────────────────────────────────────────

@Composable
private fun LockAnimation(
    isLocked: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue   = if (isLocked) 0f else -30f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "lock_rotation",
    )
    val lockColor by animateColorAsState(
        targetValue   = if (isLocked) TextSecondary else SuccessGreen,
        animationSpec = tween(400),
        label         = "lock_color",
    )

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(lockColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (isLocked) "已上鎖" else "已解鎖",
                tint               = lockColor,
                modifier           = Modifier
                    .size(64.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text       = if (isLoading) "處理中…" else if (isLocked) "已上鎖" else "已解鎖",
            color      = lockColor,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(44.dp),
        color    = SurfaceCard,
        shape    = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, fontSize = 13.sp)
        }
    }
}
